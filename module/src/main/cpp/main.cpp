#include <cstring>
#include <string>
#include <thread>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <cinttypes>
#include <dirent.h>
#include <errno.h>
#include <time.h>
#include "hack.h"
#include "zygisk.hpp"
#include "game.h"
#include "log.h"
#include "dlfcn.h"
#include "config.h"
using zygisk::Api;
using zygisk::AppSpecializeArgs;
using zygisk::ServerSpecializeArgs;

class MyModule : public zygisk::ModuleBase {
public:
    void onLoad(Api *api, JNIEnv *env) override {
        this->api = api;
        this->env = env;
        enable_hack = false;
    }

    void preAppSpecialize(AppSpecializeArgs *args) override {
        if (!args->nice_name || !args->app_data_dir) return;
        auto package_name = env->GetStringUTFChars(args->nice_name, nullptr);
        auto app_data_dir = env->GetStringUTFChars(args->app_data_dir, nullptr);
//        if (strcmp(package_name, AimPackageName) == 0){
//            args->runtime_flags=8451;
//        }
        if (!package_name || !app_data_dir) {
            if (package_name) env->ReleaseStringUTFChars(args->nice_name, package_name);
            if (app_data_dir) env->ReleaseStringUTFChars(args->app_data_dir, app_data_dir);
            return;
        }
        LOGI("preAppSpecialize %s %s %d", package_name, app_data_dir, args->runtime_flags);

        preSpecialize(package_name, app_data_dir);
        env->ReleaseStringUTFChars(args->nice_name, package_name);
        env->ReleaseStringUTFChars(args->app_data_dir, app_data_dir);
    }

    void postAppSpecialize(const AppSpecializeArgs *) override {
        if (enable_hack) {
            // Get JavaVM
            JavaVM *vm = nullptr;
            if (env->GetJavaVM(&vm) == JNI_OK) {
                // Never block app startup: the worker owns its arguments and delay.
                std::string directory(_data_dir), package(_package_name);
                delete[] _data_dir;
                delete[] _package_name;
                _data_dir = _package_name = nullptr;
                std::thread([directory, package, vm]() {
                    hack_prepare(directory.c_str(), package.c_str(), nullptr, 0, vm);
                }).detach();
            } else {
                LOGE("Failed to get JavaVM");
                delete[] _data_dir;
                delete[] _package_name;
                _data_dir = _package_name = nullptr;
            }
        }
    }

private:
    Api *api;
    JNIEnv *env;
    bool enable_hack;
    char *_data_dir = nullptr;
    char *_package_name = nullptr;
    void *data = nullptr;
    size_t length = 0;
    
    void preSpecialize(const char *package_name, const char *app_data_dir) {
        // Read configuration
        Config::reloadConfig();
        
        // Check if this app is enabled for injection
        if (Config::isAppEnabled(package_name)) {
            LOGI("成功注入目标进程: %s", package_name);
            enable_hack = true;
            _data_dir = new char[strlen(app_data_dir) + 1];
            strcpy(_data_dir, app_data_dir);
            _package_name = new char[strlen(package_name) + 1];
            strcpy(_package_name, package_name);
            
            // ConfigApp is responsible for copying SO files
            // We just need to load them

#if defined(__i386__)
            auto path = "zygisk/armeabi-v7a.so";
#endif
#if defined(__x86_64__)
            auto path = "zygisk/arm64-v8a.so";
#endif
#if defined(__i386__) || defined(__x86_64__)
            int dirfd = api->getModuleDir();
            int fd = openat(dirfd, path, O_RDONLY);
            if (fd != -1) {
                struct stat sb{};
                fstat(fd, &sb);
                length = sb.st_size;
                data = mmap(nullptr, length, PROT_READ, MAP_PRIVATE, fd, 0);
                close(fd);
            } else {
                LOGW("Unable to open arm file");
            }
#endif
        } else {
            api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
        }
    }
};

REGISTER_ZYGISK_MODULE(MyModule)