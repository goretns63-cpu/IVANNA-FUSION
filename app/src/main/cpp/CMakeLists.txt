# IVANNA-FUSION TRASCENDENTAL
# © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.

cmake_minimum_required(VERSION 3.22.1)
project("ivanna_trascendental")

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -funroll-loops -march=armv8.2-a+fp16+dotprod -fopenmp -static-openmp -fPIC -Wall -Wextra")

# Fuentes nativas
set(IVANNA_SOURCES
    audio_orchestrator.cpp
    phase_oracle.cpp
    evolutionary_kernel.cpp
    shm_hyperplane.cpp
)

add_library(ivanna_trascendental SHARED ${IVANNA_SOURCES})

find_library(log-lib log)
find_library(android-lib android)
find_library(OpenSLES-lib OpenSLES)
find_library(aaudio-lib aaudio)

target_link_libraries(ivanna_trascendental
    ${log-lib}
    ${android-lib}
    ${OpenSLES-lib}
    ${aaudio-lib}
    -fopenmp -static-openmp
)
