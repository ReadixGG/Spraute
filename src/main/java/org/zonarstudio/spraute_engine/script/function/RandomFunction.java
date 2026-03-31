package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import java.util.List;
import java.util.Random;

public class RandomFunction implements ScriptFunction {
    private static final Random RANDOM = new Random();

    @Override
    public String getName() {
        return "random";
    }

    @Override
    public int getArgCount() {
        return -1; // variable arguments
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[0];
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.isEmpty()) {
            return RANDOM.nextDouble();
        } else if (args.size() == 1) {
            if (args.get(0) instanceof Number max) {
                if (args.get(0) instanceof Integer || args.get(0) instanceof Long) {
                    int maxInt = max.intValue();
                    if (maxInt < 0) return 0;
                    return RANDOM.nextInt(maxInt + 1); // 0 to max inclusive
                }
                double maxDouble = max.doubleValue();
                if (maxDouble <= 0) return 0.0;
                return RANDOM.nextDouble() * maxDouble;
            }
        } else if (args.size() >= 2) {
            if (args.get(0) instanceof Number min && args.get(1) instanceof Number max) {
                if ((args.get(0) instanceof Integer || args.get(0) instanceof Long) &&
                    (args.get(1) instanceof Integer || args.get(1) instanceof Long)) {
                    int minInt = min.intValue();
                    int maxInt = max.intValue();
                    if (minInt > maxInt) {
                        int temp = minInt;
                        minInt = maxInt;
                        maxInt = temp;
                    }
                    int range = maxInt - minInt + 1;
                    if (range <= 0) return minInt;
                    return minInt + RANDOM.nextInt(range); // min to max inclusive
                }
                double minDouble = min.doubleValue();
                double maxDouble = max.doubleValue();
                if (minDouble > maxDouble) {
                    double temp = minDouble;
                    minDouble = maxDouble;
                    maxDouble = temp;
                }
                return minDouble + (maxDouble - minDouble) * RANDOM.nextDouble(); // min to max
            }
        }
        return RANDOM.nextDouble();
    }
}