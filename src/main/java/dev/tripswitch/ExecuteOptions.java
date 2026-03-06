package dev.tripswitch;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Per-call configuration for Execute. Package-private.
 */
class ExecuteOptions {
    List<String> breakers = new ArrayList<>();
    Function<List<BreakerMeta>, List<String>> breakerSelector;
    String routerId;
    Function<List<RouterMeta>, String> routerSelector;
    Map<String, Object> metrics = new HashMap<>();
    BiFunction<Object, Exception, Map<String, Double>> deferredMetrics;
    Map<String, String> tags = new HashMap<>();
    List<Class<? extends Exception>> ignoreErrors = new ArrayList<>();
    Predicate<Exception> errorEvaluator;
    String traceId;
}
