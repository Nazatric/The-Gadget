cmake_minimum_required(VERSION 3.22.1)
project(gadget_audio)

add_library(gadget_audio SHARED gadget_audio.cpp)

target_compile_features(gadget_audio PRIVATE cxx_std_17)
target_link_options(gadget_audio PRIVATE "-Wl,-z,max-page-size=16384")

find_library(log-lib log)
target_link_libraries(gadget_audio ${log-lib})
