public class TestNeo {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("net.neoforged.neoforge.transfer.transaction.TransactionContext");
        System.out.println("Methods for TransactionContext:");
        for (java.lang.reflect.Method m : clazz.getMethods()) {
            System.out.println(m.toString());
        }
        System.out.println("\nMethods for Transaction:");
        Class<?> clazz2 = Class.forName("net.neoforged.neoforge.transfer.transaction.Transaction");
        for (java.lang.reflect.Method m : clazz2.getMethods()) {
            System.out.println(m.toString());
        }
    }
}
