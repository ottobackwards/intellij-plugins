interface Foo {
  int fooA;

  void fooB();
}
interface Bar {
  void barA();
}
class A implements Foo, Bar {

  int fooA() {
  }

  void fooB() {
  }

  void barA() {
  }
}

main() {
  new A();
}