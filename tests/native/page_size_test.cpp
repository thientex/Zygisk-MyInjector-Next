#include <cassert>
#include "../../module/src/main/cpp/mylinker/include/common.h"
extern "C" long __real_sysconf(int);
extern "C" long __wrap_sysconf(int name) {
    if (name == _SC_PAGESIZE) return atol(getenv("TEST_PAGE_SIZE"));
    return __real_sysconf(name);
}
int main() {
    const size_t expected = atol(getenv("TEST_PAGE_SIZE"));
    assert(PAGE_SIZE == expected);
    for (size_t address : {size_t(0), size_t(4095), size_t(4096), size_t(16383), size_t(16384), size_t(65537)}) {
        assert(PAGE_START(address) == address - address % expected);
        assert(PAGE_OFFSET(address) == address % expected);
        assert(PAGE_END(address) == (address ? ((address + expected - 1) / expected) * expected : 0));
    }
}
