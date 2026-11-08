#pragma once

#include <android/log.h>
#include <elf.h>
#include <link.h>
#include <dlfcn.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <cerrno>
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <cstdint>

#include <string>
#include <vector>
#include <unordered_map>
#include <memory>

#define LOG_TAG "MyInjector"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

#if defined(__LP64__)
#define ELFW(what) ELF64_ ## what
#else
#define ELFW(what) ELF32_ ## what
#endif

// Android 15+ ARM64 devices may use 16 KiB pages. Never assume 4096 for mmap/mprotect.
inline size_t injector_page_size() {
    static const long detected = sysconf(_SC_PAGESIZE);
    return detected > 0 ? static_cast<size_t>(detected) : 4096;
}
#ifdef PAGE_SIZE
#undef PAGE_SIZE
#endif
#define PAGE_SIZE (injector_page_size())
#define PAGE_MASK (~(PAGE_SIZE - 1))
#define PAGE_START(addr) ((addr) & PAGE_MASK)
#define PAGE_END(addr) PAGE_START((addr) + PAGE_SIZE - 1)
#define PAGE_OFFSET(addr) ((addr) & (PAGE_SIZE - 1))

// 权限标志转换
#define PFLAGS_TO_PROT(x) (((x) & PF_R) ? PROT_READ : 0) | \
                          (((x) & PF_W) ? PROT_WRITE : 0) | \
                          (((x) & PF_X) ? PROT_EXEC : 0)

struct soinfo;
class ElfReader;
class MemoryManager;
class Relocator;