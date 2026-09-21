package com.contentaggregator.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

import com.contentaggregator.testutil.TestTags;

/**
 * Mark a class as a unit test (Mockito-only or plain POJO, no Spring).
 *
 * <p><b>Why no {@code @Execution(ExecutionMode.CONCURRENT)} here.</b>
 * Audit IMP-02 suggested making unit tests concurrent at the method level
 * for higher CPU utilisation. That is unsafe project-wide:
 * <ul>
 *   <li>Mockito's {@link org.mockito.junit.jupiter.MockitoExtension} runs in
 *       {@code STRICT_STUBS} mode by default. Strict-stubs session state
 *       and {@code verify(..., times(n))} accounting are not thread-safe;
 *       parallel methods sharing the same mock surface cause spurious
 *       failures and stub-leakage between tests.</li>
 *   <li>{@code @Captor}-based argument capture maintains a list inside the
 *       same {@code MockitoExtension} instance; concurrent methods append
 *       to it from multiple threads without synchronisation.</li>
 *   <li>Several existing unit tests (UserServiceTest, ContentServiceTest,
 *       ParsingSchedulerTest, ...) carry an explicit comment forbidding
 *       CONCURRENT for these reasons.</li>
 * </ul>
 *
 * <p>Default JUnit 5 parallelism (classes concurrent, methods serial
 * within a class — see {@code junit-platform.properties}) is the
 * correct trade-off for now. If a specific Mockito-free test family
 * ever needs in-class parallelism, introduce a dedicated annotation
 * (e.g. {@code @ConcurrentUnitTest}) rather than relaxing it here.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Tag(TestTags.UNIT)
@Tag(TestTags.FAST)
public @interface UnitTest {}
