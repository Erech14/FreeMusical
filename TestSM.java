import android.os.SharedMemory;
public class TestSM {
    public static void main(String[] args) {
        for (java.lang.reflect.Method m : SharedMemory.class.getDeclaredMethods()) {
            System.out.println(m.getName());
        }
    }
}
