cmake_minimum_required(VERSION 3.4.1)
project("ics-openvpn")
# Git version string

# Veil VPN: vendored snapshot (no .git) of ics-openvpn v0.7.65, openvpn submodule 566ebc9f.
set(OPENVPN2_GIT "icsopenvpn-v0.7.65-566ebc9f")
set(OPENVPN3_GIT "disabled")
message("OpenVPN 2.x version ${OPENVPN2_GIT} (OpenVPN3 disabled)")

# Make builds (like F-droid) happy that want reproducible builds
add_link_options("LINKER:--build-id=none")

# Set mbedtls options
OPTION(ENABLE_PROGRAMS "" OFF)
OPTION(USE_SHARED_MBEDTLS_LIBRARY "" OFF)
OPTION(ENABLE_TESTING "" OFF)

# Own options
OPTION(OPENVPN2MBED "Use mbed TLS for OpenVPN2" OFF)
OPTION(OPENVPN3OSSL "Use OpenSSL for OpenVPN3" ON)
SET(OPENVPN3OSSL ON)

# STATIC or SHARED
SET(SSLLIBTYPE STATIC)
SET(OPENSSL_PATH "openssl")

set(CMAKE_CXX_STANDARD 23)


#add_subdirectory(lzo)
include(tools.cmake)
include(lzo.cmake)
include(lz4.cmake)
include(openssl/openssl.cmake)

if(NOT ${OPENVPN3OSSL} OR ${OPENVPN2MBED})
    add_subdirectory(mbedtls)
endif()

# Veil VPN: OpenVPN3 (swig) and SSL speed test removed - skeleton/ovpn2 only.

add_library(ovpnutil SHARED ovpnutil/jniglue.c)
target_compile_definitions(ovpnutil PRIVATE -DTARGET_ARCH_ABI=\"${ANDROID_ABI}\"
        -DOPENVPN2_GIT_REVISION=\"${OPENVPN2_GIT}\"
        -DOPENVPN3_GIT_REVISION=\"${OPENVPN3_GIT}\"
        )
target_link_libraries(ovpnutil log)

add_library(osslutil SHARED ovpnutil/osslutil.cpp)
target_link_libraries(osslutil log crypto ssl)

set(openvpn_srcs
        src/compat/compat-basename.c
        src/compat/compat-daemon.c
        src/compat/compat-dirname.c
        src/compat/compat-gettimeofday.c
        src/openvpn/argv.c
        src/openvpn/acc.c
        src/openvpn/auth_token.c
        src/openvpn/base64.c
        src/openvpn/buffer.c
        src/openvpn/check_file_access.c
        src/openvpn/check_file_access.h
        src/openvpn/clinat.c
        src/openvpn/console.c
        src/openvpn/console_builtin.c
        src/openvpn/crypto.c
        src/openvpn/crypto_epoch.c
        src/openvpn/crypto_openssl.c
        src/openvpn/crypto_mbedtls.c
        src/openvpn/cryptoapi.c
        src/openvpn/dhcp.c
        src/openvpn/dns.c
        src/openvpn/dns.h
        src/openvpn/error.c
        src/openvpn/event.c
        src/openvpn/env_set.c
        src/openvpn/fdmisc.c
        src/openvpn/forward.c
        src/openvpn/fragment.c
        src/openvpn/gremlin.c
        src/openvpn/helper.c
        src/openvpn/httpdigest.c
        src/openvpn/init.c
        src/openvpn/interval.c
        src/openvpn/list.c
        src/openvpn/lladdr.c
        src/openvpn/lzo.c
        src/openvpn/manage.c
        src/openvpn/mbuf.c
        src/openvpn/misc.c
        src/openvpn/mroute.c
        src/openvpn/mss.c
        src/openvpn/mtcp.c
        src/openvpn/mtu.c
        src/openvpn/mudp.c
        src/openvpn/multi.c
        src/openvpn/multi_io.c
        src/openvpn/occ.c
        src/openvpn/openvpn.c
        src/openvpn/options.c
        src/openvpn/options_parse.c
        src/openvpn/options_show.c
        src/openvpn/options_show.h
        src/openvpn/options_string.c
        src/openvpn/options_string.h
        src/openvpn/options_util.c
        src/openvpn/otime.c
        src/openvpn/packet_id.c
        src/openvpn/ping.c
        src/openvpn/pkcs11.c
        src/openvpn/pkcs11_openssl.c
        src/openvpn/platform.c
        src/openvpn/plugin.c
        src/openvpn/pool.c
        src/openvpn/proto.c
        src/openvpn/proxy.c
        src/openvpn/ps.c
        src/openvpn/push.c
        src/openvpn/push_util.c
        src/openvpn/reflect_filter.c
        src/openvpn/reliable.c
        src/openvpn/route.c
        src/openvpn/run_command.c
        src/openvpn/schedule.c
        src/openvpn/session_id.c
        src/openvpn/shaper.c
        src/openvpn/sig.c
        src/openvpn/siphash.c
        src/openvpn/siphash_reference.c
        src/openvpn/socket.c
        src/openvpn/socket_util.c
        src/openvpn/socks.c
        src/openvpn/ssl.c
        src/openvpn/ssl_openssl.c
        src/openvpn/ssl_mbedtls.c
        src/openvpn/ssl_ncp.c
        src/openvpn/ssl_pkt.c
        src/openvpn/ssl_pkt.h
        src/openvpn/ssl_util.c
        src/openvpn/ssl_verify.c
        src/openvpn/ssl_verify_openssl.c
        src/openvpn/ssl_verify_mbedtls.c
        src/openvpn/status.c
        src/openvpn/tls_crypt.c
        src/openvpn/tun.c
        src/openvpn/tun_afunix.c
        src/openvpn/vlan.c
        src/openvpn/xkey_helper.c
        src/openvpn/xkey_provider.c
        src/openvpn/comp-lz4.c
        src/openvpn/comp.c
        src/openvpn/compstub.c
        )

PREPEND(openvpn_srcs_with_path "openvpn" ${openvpn_srcs})

add_library(openvpn SHARED ${openvpn_srcs_with_path})

target_include_directories(openvpn PRIVATE
        openvpn-config
        openvpn/src/compat
        openvpn/include
        mbedtls/include
        lzo/include
        openvpn
        )
target_compile_definitions(openvpn PRIVATE
        -DHAVE_CONFIG_H
        -DCONFIGURE_GIT_REVISION=\"${OPENVPN2_GIT}\"
        -DCONFIGURE_GIT_FLAGS=\"\"
        -DTARGET_ABI=\"${ANDROID_ABI}\"
        -DOPENSSL_API_COMPAT=0x11000000L
        -DDEFAULT_DNS_UPDOWN=\"/not/supported/on/android\"
        )

if (${OPENVPN2MBED})
    target_compile_definitions(openvpn PRIVATE
            -DENABLE_CRYPTO_MBEDTLS=1
            )
    target_link_libraries(openvpn mbedtls mbedx509 mbedcrypto lzo lz4)
else ()
    target_compile_definitions(openvpn PRIVATE
            -DENABLE_CRYPTO_OPENSSL=1
            )
    target_link_libraries(openvpn crypto ssl lzo lz4)
endif ()

add_executable(libovpnexec.so minivpn/minivpn.c)
target_compile_options(libovpnexec.so PRIVATE -fPIE)
target_link_libraries(libovpnexec.so PRIVATE openvpn -fPIE -pie)

# Veil VPN: pie_openvpn asset copies removed; VPNLaunchHelper always uses
# libovpnexec.so from nativeLibraryDir (app packages jniLibs with useLegacyPackaging).

# Make sure the exec target is always built together with the JNI libs.
add_dependencies(ovpnutil libovpnexec.so)
