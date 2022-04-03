# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.18.1)

# Declares and names the project.

project("androlo")

set (OpenCV_STATIC ON)
set (OpenCV_DIR $ENV{OPENCV_ANDROID}/sdk/native/jni)
message(${OpenCV_DIR})
message("hihihi")
find_package(OpenCV REQUIRED)

set(TFlite_DIR $ENV{TFLITE_ANDROID})
add_library(libtensorflowlite_jni SHARED IMPORTED)
set_target_properties(libtensorflowlite_jni PROPERTIES IMPORTED_LOCATION ${TFlite_DIR}/jni/${ANDROID_ABI}/libtensorflowlite_jni.so)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
        androlo

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        native-lib.cpp)

target_include_directories(
        androlo PRIVATE
        ${TFlite_DIR}/headers
)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)
find_library(android-lib android)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        androlo

        libtensorflowlite_jni
        ${OpenCV_LIBS}
        ${android-lib}

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})