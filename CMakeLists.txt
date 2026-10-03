cmake_minimum_required(VERSION 3.10)
project(HoshinaAssistantNative LANGUAGES C CXX)

set(APP_NAME Demo)
set(SDK_ROOT_PATH ${CMAKE_CURRENT_SOURCE_DIR}/tmp_live2d_native_sdk/CubismSdkForNative-5-r.5)
set(CORE_PATH ${SDK_ROOT_PATH}/Core)
set(FRAMEWORK_PATH ${SDK_ROOT_PATH}/Framework)
set(COMMON_SRC_PATH ${SDK_ROOT_PATH}/Samples/Common)
set(THIRD_PARTY_PATH ${SDK_ROOT_PATH}/Samples/OpenGL/thirdParty)
set(STB_PATH ${THIRD_PARTY_PATH}/stb)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(Live2DCubismCore STATIC IMPORTED)
set_target_properties(
    Live2DCubismCore
    PROPERTIES
        IMPORTED_LOCATION ${CORE_PATH}/lib/android/${ANDROID_ABI}/libLive2DCubismCore.a
        INTERFACE_INCLUDE_DIRECTORIES ${CORE_PATH}/include
)

set(FRAMEWORK_SOURCE OpenGL)
add_subdirectory(${FRAMEWORK_PATH} ${CMAKE_CURRENT_BINARY_DIR}/Framework)
target_compile_definitions(Framework PUBLIC CSM_TARGET_ANDROID_ES2)

add_library(${APP_NAME} SHARED)
add_subdirectory(${COMMON_SRC_PATH} ${CMAKE_CURRENT_BINARY_DIR}/Common)
add_subdirectory(app/src/main/cpp/live2d)

target_link_libraries(
    ${APP_NAME}
    Framework
    Live2DCubismCore
    GLESv2
    log
)

target_include_directories(${APP_NAME} PRIVATE ${STB_PATH})
target_link_options(${APP_NAME} PRIVATE "-Wl,-z,max-page-size=16384")
