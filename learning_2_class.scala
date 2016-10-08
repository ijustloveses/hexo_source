class Basic {                // 这里由于默认主构造器没有参数，故此省略了括号
    private var age = 0
    def incr() {age += 1}    // 这里没有 = 等于号
    def decr() = {age -= 1}
    def cur = age            // 这里省略了括号
}

class GetterSetter {
    var age = 0              // 自动生成 Getter & Setter
    val gender = 'F'         // 自动生成 Getter，由于不可变，故此不生成 Setter
    private var name = _     // 不会自动生成 Getter & Setter；另，name 初始化为空
    def pubname = name       // 外部可以使用 pubname 来使用 name
}

class PrivateAttr {
    private var privateAge = 0            // 类级别的私有属性
    def age = privateAge

    private[this] var privateWeight = 0   // 私有属性，不仅是类私有，而且是实例私有，不能被同类的其他实例访问
    def weight = privateWeight

    def isYounger(other: PrivateAttr) = privateAge < other.privateAge
    // def isThinner(other: PrivateAttr) = privateWeight < other.privateWeight  这个会失败，因为是私有属性 private[this]
}

class Constructor {           // 默认的主构造器没有参数，省略了括号
    var name : String = _
    private var age = 27
    private[this] val gender = "male"

    def this(name: String) {
        this                  // 调用主构造器，重载的构造器都间接或直接的调用默认主构造器
        this.name = name
    }

    def say() {
        println(this.name + ":" + this.age + ":" + this.gender)
    }
}

class PrivateConstructor private (val name: String, val age: Int) {    // 参数前加 var 或 val，就表示参数为类成员变量；private 表示主构造器不能外部调用
    println("This is primary constructor")
    var gender : String = _

    def this(name: String, age: Int, gender: String) {
        this(name, age)
        this.gender = gender
    }

    def say() {
        println(this.name + ":" + this.age + ":" + this.gender)
    }
}

class Outer(val name: String) { outer =>    // 定义 Inner 时，自动生成 Inner 所属的 Outer 实例 outer
    class Inner(val name: String) {
        def foo(b:Inner) = println("Outer: " + outer.name +    // Inner 类中可以直接访问所属的 Outer 实例 outer
            "  Inner: " + b.name)
    }
}

object Singleton {         // object 相当于静态类，不需要实例化，也可以理解为单例
    private var cnt = 0
    def incr = {           // public 的；如果前面加上 private，外界不能调用
        cnt += 1
        cnt
    }
    private def private_incr = {    // private 的，可以被伴生类调用
        cnt += 1
        cnt
    }
}

class Singleton {          // object Singleton 的伴生类
    val cur_cnt = Singleton.private_incr    // 可以访问 private 成员
}

class ObjApply {
    def foo {
        println("Have a try on object-level apply")
    }
}

object ObjApply {
    // apply 函数，就是提供主体被调用的功能
    def apply() = {        // obj 级的 apply，直接调用 object 主体，即 ObjApply()；类似 Array(1,2,3)
        new ObjApply       // 调用后，生成一个 ObbjApply() 类的实例
    }
}

class ClassApply {
    // class 级的 apply，需要创建实例，然后把实例当作函数调用
    def apply() = println("Have a try on class-level apply")
}

object Hello {
    def main(args: Array[String]): Unit = {
        test_basic
        test_getter_setter
        test_private_attr
        test_constructor
        test_private_constructor
        test_outer_inner
        test_singleton
        test_friend_class
        test_obj_apply
        test_class_apply
    }

    def print_banner(msg: String): Unit = {
        println("---------- " + msg + " ----------")
    }

    def test_basic(): Unit = {
        print_banner("test basic")
        val b = new Basic()
        b.incr()
        b.incr()
        b.decr()
        println("current age is " + b.cur)     // 这里调用也没有使用括号
    }

    def test_getter_setter(): Unit = {
        print_banner("test getter setter")
        gs = new GetterSetter()
        gs.age = 25
        println("age is " + gs.age)
        // gs.gender = "M"                     // 这句会报错，不能 set
        println("gender is " + gs.gender)
        gs.pubname = "John"                    // pubname 既可以 set 又可以 get
        println("name is " + gs.pubname)
    }

    def test_private_attr(): Unit = {
        print_banner("test private attr")
        pa1 = new PrivateAttr()
        pa2 = new PrivateAttr()
        pa1.age = 5
        pa2.age = 27
        println(if pa1.isYounger(pa2) "yes, younger" else "no, older")   // output "yes, younger"
    }

    def test_constructor(): Unit = {
        print_banner("test constructor")
        cons1 = new Constructor()
        cons1.name = "John"
        cons2 = new Constructor("Leon")
        cons1.say
        cons2.say
    }

    def test_private_constructor(): Unit = {
        print_banner("test private constructor")
        // con = new PrivateConstructor("John", 25)   这个会报错，因为是 private 的
        con = new PrivateConstructor("John", 25, "male")
        con.say
    }

    def test_outer_inner(): Unit = {
        val o1 = new Outer("O1")
        val o2 = new Outer("O2")
        val i1 = new o1.Inner("I1")      // 注意看内部类初始化时，是要指定其所属外部类实例的
        val i2 = new o2.Inner("I2")
        i1.foo(i1)   // Outer: O1  Inner: I1
        i2.foo(i2)   // Outer: O2  Inner: I2
        // i1.foo(i2)  这个会失败，因为 i1 所属的 Outer 实例是 o1
    }

    def test_singleton(): Unit = {
        println("1st incr: " + Singleton.incr)      // 1，直接使用静态类名，不需要实例化
        println("2nd incr: " + Singleton.incr)      // 2
    }

    def test_friend_class(): Unit = {
        val fc = new Singleton()
        println("cnt after incr: " + fc.cur_cnt)
    }

    def test_obj_apply(): Unit = {
        val a = ObjApply()         // 把 object 名字当作函数来调用，内部调用 apply
        a.foo
    }

    def test_class_apply(): Unit = {
        val a = new ClassApply()
        a()                        // 把类的实例当作函数来调用，内部调用 apply
    }
}
