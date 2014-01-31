/**
 * Copyright 2013, Landz and its contributors. All rights reserved.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package z.util;

import z.annotation.PerformanceDegraded;
import z.function.ThrowableFunction0;
import z.function.ThrowableRunnable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Function;

/**
 * Throwables provides various verbs for exception side operations.
 */
public final class Throwables {

  private static final
  Function<Throwable, RuntimeException>
      DEFAULT_EXCEPTION_SUPPLIER = RuntimeException::new;

  public static void uncheck(ThrowableRunnable runnable,
                             Function<Throwable, RuntimeException> expThrower) {
    try {
      runnable.run();
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable e) {
      throw expThrower.apply(e);
    }
  }

  public static void uncheck(ThrowableRunnable fn) {
    uncheck(fn, DEFAULT_EXCEPTION_SUPPLIER);
  }

  /**
   * this static method will return the result of the lambda apply
   *
   * @param fn
   * @param expThrower
   * @param <R>
   * @return
   */
  public static <R> R uncheckTo(ThrowableFunction0<R> fn,
                                Function<Throwable, RuntimeException> expThrower) {
    try {
      return fn.apply();
    } catch (Throwable e) {
      throw expThrower.apply(e);
    }
  }

  /**
   * this static method will return the result of the lambda apply
   *
   * @param fn
   * @param <R>
   * @return
   */
  public static <R> R uncheckTo(ThrowableFunction0<R> fn) {
    return uncheckTo(fn, DEFAULT_EXCEPTION_SUPPLIER);
  }


  /**
   * note: this method marked with @PerformanceDegraded
   *
   * @param runnable
   * @return
   */
  @PerformanceDegraded("z.util.Throwables.ExceptionMatcher")
  public static ExceptionMatcher check(ThrowableRunnable runnable) {
    return (expClass, expHandler) -> {
      try {
        runnable.run();
      } catch (Throwable e) {
        if (e.getClass().equals(expClass))
          expHandler.handle(e);
        throw new RuntimeException(e);
      }
    };

  }

  @FunctionalInterface
  public static interface ExceptionHandler {
    void handle(Throwable e);
  }

  @FunctionalInterface
  public static interface ExceptionMatcher {

    default void onExceptions(Class<? extends Exception>[] expClass, ExceptionHandler expHandler) {
    }

    void onException(Class<? extends Exception> expClass, ExceptionHandler expHandler);
  }

//    //========================================= ... check ... onException ...
//    @PerformanceDegraded
//    public static ExceptionMatcher onException(Class<? extends Exception> expClass, ExceptionHandler expHandler) {
//        return (ec, eh) -> {
//            Stream.builder().add();
//        };
//    }
//
//    @FunctionalInterface
//    public static interface SelfReturnableExceptionCollector {
//
//        SelfReturnableExceptionCollector onException(Class<? extends Exception> expClass, ExceptionHandler expHandler);
//
//    }

  /**
   * Returns a string containing the result of
   * {@link Throwable#toString() toString()}, followed by the full, recursive
   * stack trace of {@code throwable}. Note that you probably should not be
   * parsing the resulting string; if you need programmatic access to the stack
   * frames, you can call {@link Throwable#getStackTrace()}.
   *
   * @origin guava
   */
  public static String getStackTraceAsString(Throwable throwable) {
    StringWriter stringWriter = new StringWriter();
    throwable.printStackTrace(new PrintWriter(stringWriter));
    return stringWriter.toString();
  }

}