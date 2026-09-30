package com.contentaggregator.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.contentaggregator.testutil.TestResources;
import com.contentaggregator.testutil.TestTags;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@IntegrationTest
@Tag(TestTags.DATABASE)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(value = TestResources.POSTGRES, mode = ResourceAccessMode.READ_WRITE)
public @interface DatabaseTest {}
