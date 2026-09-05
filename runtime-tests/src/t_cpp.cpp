// t_cpp — glibc C++: libstdc++ + libgcc_s, iostream, std::string, exceptions
// thrown across function boundaries, dynamic_cast.
#include <iostream>
#include <string>
#include <stdexcept>

struct Base { virtual ~Base() = default; virtual int id() const { return 1; } };
struct Derived : Base { int id() const override { return 2; } };

static int thrower(bool throw_it) {
    if (throw_it) throw std::runtime_error("cpp-exc");
    return 0;
}

int main() {
    try {
        std::string s = "cpp";
        s += "-ok";
        std::cout << s << std::endl;
        thrower(true);
        std::cout << "cpp-FAIL(no-throw)" << std::endl;
        return 1;
    } catch (const std::exception &e) {
        if (std::string(e.what()) != "cpp-exc") return 1;
    }
    Derived d;
    Base *b = &d;
    if (dynamic_cast<Derived *>(b) == nullptr) return 1;
    return 0;
}
