import android.os.SharedMemory

fun main() {
    val methods = SharedMemory::class.java.methods
    for (m in methods) {
        println(m.name)
    }
}
