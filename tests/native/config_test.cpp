#include "../../module/src/main/cpp/config.cpp"
#include <cassert>
int main() {
    assert(Config::boundedNumber("0", 2, 0, 60) == 0);
    assert(Config::boundedNumber("60", 2, 0, 60) == 60);
    for (const auto *value : {"-1", "61", "oops", "99999999999999999999999999", "3oops", ""})
        assert(Config::boundedNumber(value, 2, 0, 60) == 2);
    Config::parseAppConfig("example.app", R"({"enabled":true,"injectionMethod":"standard",
      "soFiles":[{"name":"example.so","storedPath":"/tmp/example.so"}],
      "gadgetConfig":{"port":9999999999999999999999999999}})");
    assert(Config::g_config.perAppConfig.at("example.app").enabled);
    assert(Config::g_config.perAppConfig.at("example.app").soFiles.size() == 1);
    assert(Config::g_config.perAppConfig.at("example.app").gadgetConfig->port == 27042);
}
