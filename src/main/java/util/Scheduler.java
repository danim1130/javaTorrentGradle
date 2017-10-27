package util;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

public class Scheduler {
    private static ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(16){ //yay for scheduled executors not having exception handler
        @Override
        public Future<?> submit(Runnable task) {
            Callable<?> wrappedTask = () -> {
                try {
                    task.run();
                }
                catch (Exception e) {
                    System.out.println("Oh boy, something broke!");
                    e.printStackTrace();
                    throw e;
                }
                return null;
            };

            return super.submit(wrappedTask);
        }
    };
    ;

    public static ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return scheduler.schedule(command, delay, unit);
    }

    public static <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return scheduler.schedule(callable, delay, unit);
    }

    public static ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return scheduler.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    public static ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return scheduler.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    public static void shutdown() {
        scheduler.shutdown();
    }

    public static List<Runnable> shutdownNow() {
        return scheduler.shutdownNow();
    }

    public static boolean isShutdown() {
        return scheduler.isShutdown();
    }

    public static boolean isTerminated() {
        return scheduler.isTerminated();
    }

    public static boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return scheduler.awaitTermination(timeout, unit);
    }

    public static <T> Future<T> submit(Callable<T> task) {
        return scheduler.submit(task);
    }

    public static <T> Future<T> submit(Runnable task, T result) {
        return scheduler.submit(task, result);
    }

    public static Future<?> submit(Runnable task) {
        return scheduler.submit(task);
    }

    public static <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return scheduler.invokeAll(tasks);
    }

    public static <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return scheduler.invokeAll(tasks, timeout, unit);
    }

    public static <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return scheduler.invokeAny(tasks);
    }

    public static <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return scheduler.invokeAny(tasks, timeout, unit);
    }

    public static void execute(Runnable command) {
        scheduler.execute(command);
    }
}
