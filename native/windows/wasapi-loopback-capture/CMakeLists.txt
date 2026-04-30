cmake_minimum_required(VERSION 3.16)
project(wasapi_loopback_capture LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_executable(wasapi-loopback-capture
    src/main.cpp
)

target_compile_definitions(wasapi-loopback-capture PRIVATE UNICODE _UNICODE NOMINMAX)
target_link_libraries(wasapi-loopback-capture PRIVATE ole32 avrt winmm)

if (MSVC)
    target_compile_options(wasapi-loopback-capture PRIVATE /W4 /EHsc)
endif()
