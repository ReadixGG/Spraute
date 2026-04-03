package org.zonarstudio.spraute_engine;

public class TestMethods {
    public static void main(String[] args) throws Exception {
        System.out.println("Methods of EntityType.Builder:");
        for (java.lang.reflect.Method m : net.minecraft.world.entity.EntityType.Builder.class.getDeclaredMethods()) {
            System.out.println(m.getName() + " " + java.util.Arrays.toString(m.getParameterTypes()));
        }
    }
}
