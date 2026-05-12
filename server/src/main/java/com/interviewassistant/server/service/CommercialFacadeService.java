package com.interviewassistant.server.service;

import com.interviewassistant.server.config.AssistantProperties;
import com.interviewassistant.server.dto.AuthResponse;
import com.interviewassistant.server.dto.BalanceTransactionResponse;
import com.interviewassistant.server.dto.CreateOrderRequest;
import com.interviewassistant.server.dto.FinishUsageSessionRequest;
import com.interviewassistant.server.dto.HeartbeatUsageSessionRequest;
import com.interviewassistant.server.dto.LoginRequest;
import com.interviewassistant.server.dto.MockPaymentCallbackRequest;
import com.interviewassistant.server.dto.OrderResponse;
import com.interviewassistant.server.dto.PaymentNotifyResult;
import com.interviewassistant.server.dto.PlanResponse;
import com.interviewassistant.server.dto.RegisterRequest;
import com.interviewassistant.server.dto.StartUsageSessionRequest;
import com.interviewassistant.server.dto.UsageSessionResponse;
import com.interviewassistant.server.dto.UserProfileResponse;
import com.interviewassistant.server.entity.BalanceTransaction;
import com.interviewassistant.server.entity.CommercialOrder;
import com.interviewassistant.server.entity.CommercialPlan;
import com.interviewassistant.server.entity.UsageSession;
import com.interviewassistant.server.entity.UserAccount;
import com.interviewassistant.server.repository.BalanceTransactionRepository;
import com.interviewassistant.server.repository.CommercialOrderRepository;
import com.interviewassistant.server.repository.CommercialPlanRepository;
import com.interviewassistant.server.repository.UsageSessionRepository;
import com.interviewassistant.server.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Service
public class CommercialFacadeService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final String DEMO_USER_ID = "user-demo-001";

    private final UserAccountRepository userAccountRepository;
    private final CommercialPlanRepository commercialPlanRepository;
    private final CommercialOrderRepository commercialOrderRepository;
    private final UsageSessionRepository usageSessionRepository;
    private final BalanceTransactionRepository balanceTransactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AssistantProperties assistantProperties;

    public CommercialFacadeService(UserAccountRepository userAccountRepository,
                                   CommercialPlanRepository commercialPlanRepository,
                                   CommercialOrderRepository commercialOrderRepository,
                                   UsageSessionRepository usageSessionRepository,
                                   BalanceTransactionRepository balanceTransactionRepository,
                                   PasswordEncoder passwordEncoder,
                                   JwtTokenService jwtTokenService,
                                   AssistantProperties assistantProperties) {
        this.userAccountRepository = userAccountRepository;
        this.commercialPlanRepository = commercialPlanRepository;
        this.commercialOrderRepository = commercialOrderRepository;
        this.usageSessionRepository = usageSessionRepository;
        this.balanceTransactionRepository = balanceTransactionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.assistantProperties = assistantProperties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        userAccountRepository.findByPhone(request.getPhone()).ifPresent(user -> {
            throw new IllegalArgumentException("手机号已注册");
        });

        UserAccount user = new UserAccount();
        user.setPhone(request.getPhone());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() == null || request.getNickname().isBlank() ? "新用户" : request.getNickname());
        user.setStatus("ACTIVE");
        user.setRole(resolveRole(request.getPhone()));
        return buildAuthResponse(userAccountRepository.save(user));
    }

    public AuthResponse login(LoginRequest request) {
        UserAccount user = userAccountRepository.findByPhone(request.getPhone())
            .orElseGet(() -> createDemoUserIfMatched(request));
        if (!passwordMatches(request.getPassword(), user.getPasswordHash())) {
            throw new SecurityException("手机号或密码错误");
        }
        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String userId) {
        UserAccount user = resolveUser(userId);
        List<BalanceTransaction> transactions = balanceTransactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        int remainingSeconds = transactions.stream().mapToInt(this::transactionSeconds).sum();
        int remainingMinutes = (int) Math.ceil(Math.max(0, remainingSeconds) / 60.0);
        int usedSeconds = transactions.stream()
            .mapToInt(this::transactionSeconds)
            .filter(seconds -> seconds < 0)
            .map(Math::abs)
            .sum();
        int usedMinutes = (int) Math.ceil(usedSeconds / 60.0);
        CommercialOrder latestPaidOrder = commercialOrderRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
            .filter(order -> "PAID".equals(order.getStatus()))
            .findFirst()
            .orElse(null);
        String currentPlanName = latestPaidOrder == null ? "暂无套餐" : latestPaidOrder.getPlanName();
        String expiryTime = latestPaidOrder == null
            ? null
            : commercialPlanRepository.findById(latestPaidOrder.getPlanId())
                .map(plan -> latestPaidOrder.getPaidAt().plusDays(plan.getValidDays()).format(FORMATTER))
                .orElse(latestPaidOrder.getPaidAt().plusDays(30).format(FORMATTER));

        return new UserProfileResponse(
            user.getId(),
            user.getPhone(),
            user.getNickname(),
            user.getStatus(),
            user.getRole(),
            currentPlanName,
            remainingMinutes,
            remainingSeconds,
            usedMinutes,
            usedSeconds,
            expiryTime
        );
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        List<CommercialPlan> plans = commercialPlanRepository.findByStatusOrderByPriceAsc("ACTIVE");
        if (plans.isEmpty()) {
            return fallbackPlans();
        }
        return plans.stream().map(this::toPlanResponse).toList();
    }

    @Transactional
    public OrderResponse createOrder(String userId, CreateOrderRequest request) {
        UserAccount user = resolveUser(userId);
        CommercialPlan plan = resolvePlan(request.getPlanId());
        if (!"ACTIVE".equals(plan.getStatus())) {
            throw new IllegalArgumentException("套餐已下架");
        }

        CommercialOrder order = new CommercialOrder();
        order.setUserId(user.getId());
        order.setPlanId(plan.getId());
        order.setPlanName(plan.getName());
        order.setAmount(plan.getPrice());
        order.setMinutes(plan.getTotalMinutes());
        order.setStatus("PENDING");
        order.setPaymentChannel(normalizePaymentChannel(request.getPaymentChannel()));
        return toOrderResponse(commercialOrderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(String userId) {
        UserAccount user = resolveUser(userId);
        return commercialOrderRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
            .map(this::toOrderResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listRecentAdminOrders(String status) {
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        List<CommercialOrder> orders = normalizedStatus.isBlank()
            ? commercialOrderRepository.findTop100ByOrderByCreatedAtDesc()
            : commercialOrderRepository.findTop100ByStatusOrderByCreatedAtDesc(normalizedStatus);
        return orders.stream().map(this::toOrderResponse).toList();
    }

    @Transactional
    public CommercialOrder resolveOwnedPendingOrder(String userId, String orderId, String paymentChannel) {
        UserAccount user = resolveUser(userId);
        CommercialOrder order = commercialOrderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!user.getId().equals(order.getUserId())) {
            throw new SecurityException("无权操作该订单");
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new IllegalStateException("订单状态不可支付");
        }
        order.setPaymentChannel(normalizePaymentChannel(paymentChannel));
        return commercialOrderRepository.save(order);
    }

    @Transactional
    public OrderResponse markOrderPaid(String userId, MockPaymentCallbackRequest request) {
        UserAccount user = resolveUser(userId);
        CommercialOrder order = commercialOrderRepository.findById(request.getOrderId())
            .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!user.getId().equals(order.getUserId())) {
            throw new SecurityException("无权操作该订单");
        }
        return markOrderPaid(order.getId());
    }

    @Transactional
    public OrderResponse markOrderPaid(PaymentNotifyResult notifyResult) {
        CommercialOrder order = commercialOrderRepository.findById(notifyResult.getOrderId())
            .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        ensurePaymentNotifyMatchesOrder(order, notifyResult);
        if (notifyResult.getTransactionId() != null && !notifyResult.getTransactionId().isBlank()) {
            order.setPaymentTransactionId(notifyResult.getTransactionId().trim());
        }
        return markOrderPaid(order);
    }

    @Transactional
    public OrderResponse markOrderPaid(String orderId) {
        CommercialOrder order = commercialOrderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        return markOrderPaid(order);
    }

    private OrderResponse markOrderPaid(CommercialOrder order) {
        if ("PAID".equals(order.getStatus())) {
            ensureGrantTransactionExists(order);
            return toOrderResponse(order);
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new IllegalStateException("订单状态不可支付");
        }

        order.setStatus("PAID");
        order.setPaidAt(OffsetDateTime.now());
        CommercialOrder savedOrder = commercialOrderRepository.save(order);
        ensureGrantTransactionExists(savedOrder);

        return toOrderResponse(savedOrder);
    }

    private void ensureGrantTransactionExists(CommercialOrder order) {
        if (!balanceTransactionRepository.existsBySourceTypeAndSourceIdAndType("ORDER", order.getId(), "GRANT")) {
            BalanceTransaction transaction = new BalanceTransaction();
            transaction.setUserId(order.getUserId());
            transaction.setType("GRANT");
            transaction.setMinutes(order.getMinutes());
            transaction.setSourceType("ORDER");
            transaction.setSourceId(order.getId());
            balanceTransactionRepository.save(transaction);
        }
    }

    @Transactional
    public OrderResponse adminCloseOrder(String orderId, String reason) {
        CommercialOrder order = commercialOrderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if ("PAID".equals(order.getStatus())) {
            throw new IllegalStateException("已支付订单不可关闭");
        }
        if ("CLOSED".equals(order.getStatus())) {
            return toOrderResponse(order);
        }
        order.setStatus("CLOSED");
        order.setClosedAt(OffsetDateTime.now());
        order.setCloseReason(reason == null || reason.isBlank() ? "管理员手动关闭" : reason.trim());
        return toOrderResponse(commercialOrderRepository.save(order));
    }

    @Transactional
    public OrderResponse adminGrantPaidOrder(String orderId, String transactionId) {
        CommercialOrder order = commercialOrderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if ("CLOSED".equals(order.getStatus())) {
            throw new IllegalStateException("已关闭订单不可补单入账");
        }
        if (transactionId != null && !transactionId.isBlank()) {
            order.setPaymentTransactionId(transactionId.trim());
        }
        return markOrderPaid(order);
    }

    @Transactional
    public int closeExpiredPendingOrders(int timeoutMinutes) {
        OffsetDateTime deadline = OffsetDateTime.now().minusMinutes(Math.max(1, timeoutMinutes));
        List<CommercialOrder> expiredOrders = commercialOrderRepository.findByStatusAndCreatedAtBefore("PENDING", deadline);
        expiredOrders.forEach(order -> {
            order.setStatus("CLOSED");
            order.setClosedAt(OffsetDateTime.now());
            order.setCloseReason("订单超过 " + Math.max(1, timeoutMinutes) + " 分钟未支付，系统自动关闭");
        });
        commercialOrderRepository.saveAll(expiredOrders);
        return expiredOrders.size();
    }

    private void ensurePaymentNotifyMatchesOrder(CommercialOrder order, PaymentNotifyResult notifyResult) {
        if (notifyResult.getOrderId() == null || notifyResult.getOrderId().isBlank()) {
            throw new IllegalArgumentException("支付回调缺少订单号");
        }
        if (!notifyResult.getOrderId().equals(order.getId())) {
            throw new SecurityException("支付回调订单号与数据库订单不一致");
        }
        if (notifyResult.getPaymentChannel() == null || notifyResult.getPaymentChannel().isBlank()) {
            throw new SecurityException("支付回调缺少支付渠道");
        }
        if (!notifyResult.getPaymentChannel().equals(order.getPaymentChannel())) {
            throw new SecurityException("支付回调渠道与订单不一致");
        }
        if (notifyResult.getPaidAmount() == null) {
            throw new SecurityException("支付回调缺少支付金额");
        }
        if (notifyResult.getPaidAmount().compareTo(order.getAmount()) != 0) {
            throw new SecurityException("支付回调金额与订单金额不一致");
        }
        if (notifyResult.getTransactionId() == null || notifyResult.getTransactionId().isBlank()) {
            throw new SecurityException("支付回调缺少第三方交易号");
        }
        if (commercialOrderRepository.existsByPaymentChannelAndPaymentTransactionIdAndIdNot(
            notifyResult.getPaymentChannel(),
            notifyResult.getTransactionId().trim(),
            order.getId()
        )) {
            throw new SecurityException("第三方支付交易号已被其他订单使用");
        }
    }

    @Transactional
    public UsageSessionResponse startUsageSession(String userId, StartUsageSessionRequest request) {
        UserAccount user = resolveUser(userId);
        ensureUserCanUseCoreFeature(user.getId());

        UsageSession session = new UsageSession();
        session.setUserId(user.getId());
        session.setScenario(request.getScenario() == null || request.getScenario().isBlank()
            ? "INTERVIEW_ASSIST"
            : request.getScenario());
        session.setStatus("ACTIVE");
        return toUsageSessionResponse(usageSessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public List<UsageSessionResponse> listUsageSessions(String userId) {
        UserAccount user = resolveUser(userId);
        return usageSessionRepository.findByUserIdOrderByStartedAtDesc(user.getId()).stream()
            .map(this::toUsageSessionResponse)
            .toList();
    }

    @Transactional
    public UsageSessionResponse finishUsageSession(String userId, FinishUsageSessionRequest request) {
        UserAccount user = resolveUser(userId);
        UsageSession session = usageSessionRepository.findById(request.getSessionId())
            .orElseThrow(() -> new IllegalArgumentException("使用会话不存在"));
        if (!user.getId().equals(session.getUserId())) {
            throw new SecurityException("无权操作该使用会话");
        }
        if ("SETTLED".equals(session.getStatus())) {
            return toUsageSessionResponse(session);
        }

        OffsetDateTime endedAt = OffsetDateTime.now();
        long durationSeconds = Math.max(1, java.time.Duration.between(session.getStartedAt(), endedAt).toSeconds());
        int chargedSeconds = Math.toIntExact(durationSeconds);
        UserProfileResponse profile = getProfile(user.getId());
        if (profile.getRemainingSeconds() < chargedSeconds) {
            chargedSeconds = Math.max(0, profile.getRemainingSeconds());
        }
        if (chargedSeconds <= 0) {
            throw new SecurityException("可用时长不足，无法结算本次使用");
        }
        int chargedMinutes = (int) Math.ceil(chargedSeconds / 60.0);

        session.setEndedAt(endedAt);
        session.setDurationSeconds((int) durationSeconds);
        session.setChargedSeconds(chargedSeconds);
        session.setStatus("SETTLED");
        UsageSession savedSession = usageSessionRepository.save(session);

        if (!balanceTransactionRepository.existsBySourceTypeAndSourceIdAndType("SESSION", savedSession.getId(), "CONSUME")) {
            BalanceTransaction transaction = new BalanceTransaction();
            transaction.setUserId(user.getId());
            transaction.setType("CONSUME");
            transaction.setSeconds(-chargedSeconds);
            transaction.setSourceType("SESSION");
            transaction.setSourceId(savedSession.getId());
            balanceTransactionRepository.save(transaction);
        }

        return toUsageSessionResponse(savedSession);
    }

    @Transactional(readOnly = true)
    public List<BalanceTransactionResponse> listBalanceTransactions(String userId) {
        UserAccount user = resolveUser(userId);
        return balanceTransactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
            .map(this::toBalanceTransactionResponse)
            .toList();
    }

    public void ensureUserCanUseCoreFeature(String userId) {
        UserProfileResponse profile = getProfile(userId);
        if (!"ACTIVE".equals(profile.getStatus())) {
            throw new SecurityException("账号状态不可用");
        }
        if (profile.getRemainingSeconds() <= 0) {
            throw new SecurityException("可用时长不足，请先购买套餐");
        }
    }

    private AuthResponse buildAuthResponse(UserAccount user) {
        return new AuthResponse(
            jwtTokenService.generateToken(user.getId(), user.getPhone()),
            "Bearer",
            jwtTokenService.getExpireSeconds(),
            getProfile(user.getId())
        );
    }

    private boolean passwordMatches(String rawPassword, String storedPasswordHash) {
        if (storedPasswordHash != null && storedPasswordHash.startsWith("plain:")) {
            return storedPasswordHash.equals("plain:" + rawPassword);
        }
        return passwordEncoder.matches(rawPassword, storedPasswordHash);
    }

    private UserAccount resolveUser(String userId) {
        if (DEMO_USER_ID.equals(userId)) {
            return userAccountRepository.findById(DEMO_USER_ID).orElseGet(this::createDemoUser);
        }
        return userAccountRepository.findById(userId)
            .orElseThrow(() -> new SecurityException("用户不存在或未登录"));
    }

    private UserAccount createDemoUserIfMatched(LoginRequest request) {
        if (!"13800138000".equals(request.getPhone()) || !"123456".equals(request.getPassword())) {
            throw new SecurityException("手机号或密码错误");
        }
        return createDemoUser();
    }

    private UserAccount createDemoUser() {
        UserAccount user = new UserAccount();
        user.setId(DEMO_USER_ID);
        user.setPhone("13800138000");
        user.setPasswordHash(passwordEncoder.encode("123456"));
        user.setNickname("商用演示用户");
        user.setStatus("ACTIVE");
        user.setRole(resolveRole(user.getPhone()));
        return userAccountRepository.save(user);
    }

    private String resolveRole(String phone) {
        boolean isAdmin = Arrays.stream(assistantProperties.getAdminPhones().split(","))
            .map(String::trim)
            .anyMatch(adminPhone -> !adminPhone.isBlank() && adminPhone.equals(phone));
        return isAdmin ? "ADMIN" : "USER";
    }

    public boolean isAdmin(String userId) {
        return "ADMIN".equals(resolveUser(userId).getRole());
    }

    private CommercialPlan resolvePlan(String planIdOrCode) {
        return commercialPlanRepository.findById(planIdOrCode)
            .or(() -> commercialPlanRepository.findByCode(planIdOrCode))
            .orElseThrow(() -> new IllegalArgumentException("套餐不存在"));
    }

    private String normalizePaymentChannel(String paymentChannel) {
        if (paymentChannel == null || paymentChannel.isBlank()) {
            return "WECHAT";
        }
        String normalized = paymentChannel.trim().toUpperCase();
        if (!List.of("WECHAT", "ALIPAY").contains(normalized)) {
            throw new IllegalArgumentException("暂不支持该支付方式");
        }
        return normalized;
    }

    private PlanResponse toPlanResponse(CommercialPlan plan) {
        return new PlanResponse(
            plan.getCode(),
            plan.getName(),
            plan.getTotalMinutes(),
            plan.getValidDays(),
            plan.getPrice(),
            plan.getDescription(),
            Boolean.TRUE.equals(plan.getFeatured())
        );
    }

    private OrderResponse toOrderResponse(CommercialOrder order) {
        return new OrderResponse(
            order.getId(),
            order.getPlanId(),
            order.getPlanName(),
            order.getAmount(),
            order.getMinutes(),
            order.getStatus(),
            order.getPaymentChannel(),
            order.getPaymentTransactionId(),
            format(order.getPaidAt()),
            format(order.getClosedAt()),
            order.getCloseReason(),
            format(order.getCreatedAt())
        );
    }

    private UsageSessionResponse toUsageSessionResponse(UsageSession session) {
        return new UsageSessionResponse(
            session.getId(),
            session.getScenario(),
            session.getStatus(),
            format(session.getStartedAt()),
            format(session.getEndedAt()),
            session.getDurationSeconds(),
            session.getChargedMinutes(),
            session.getChargedSeconds()
        );
    }

    private BalanceTransactionResponse toBalanceTransactionResponse(BalanceTransaction transaction) {
        return new BalanceTransactionResponse(
            transaction.getId(),
            transaction.getType(),
            transaction.getMinutes(),
            transactionSeconds(transaction),
            transaction.getSourceType(),
            transaction.getSourceId(),
            resolveTransactionSourceName(transaction),
            format(transaction.getCreatedAt())
        );
    }

    private String resolveTransactionSourceName(BalanceTransaction transaction) {
        if ("ORDER".equals(transaction.getSourceType())) {
            return commercialOrderRepository.findById(transaction.getSourceId())
                .map(order -> order.getPlanName() + "套餐")
                .orElse("套餐订单");
        }
        if ("SESSION".equals(transaction.getSourceType())) {
            return usageSessionRepository.findById(transaction.getSourceId())
                .map(session -> sceneText(session.getScenario()))
                .orElse("面试使用");
        }
        return null;
    }

    private String sceneText(String scenario) {
        if ("INTERVIEW_ASSIST".equals(scenario) || "DESKTOP_INTERVIEW_ASSIST".equals(scenario)) {
            return "实时面试辅助";
        }
        if ("MOCK_INTERVIEW".equals(scenario)) {
            return "模拟面试练习";
        }
        return scenario == null || scenario.isBlank() ? "面试使用" : scenario;
    }

    private int transactionSeconds(BalanceTransaction transaction) {
        if (transaction.getSeconds() != null) {
            return transaction.getSeconds();
        }
        return transaction.getMinutes() == null ? 0 : transaction.getMinutes() * 60;
    }

    private List<PlanResponse> fallbackPlans() {
        return List.of(
            new PlanResponse("trial-30", "新人试用包", 30, 7, new BigDecimal("9.90"), "适合快速体验核心功能", false),
            new PlanResponse("boost-300", "求职冲刺包", 400, 30, new BigDecimal("99.00"), "适合面试密集阶段，主推套餐", true),
            new PlanResponse("pro-800", "长期准备包", 1000, 90, new BigDecimal("199.00"), "适合长期备战与多轮模拟", false)
        );
    }

    private String format(OffsetDateTime time) {
        return time == null ? null : time.format(FORMATTER);
    }
}
