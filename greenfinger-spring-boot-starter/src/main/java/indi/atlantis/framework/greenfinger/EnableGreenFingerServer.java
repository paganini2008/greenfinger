package indi.atlantis.framework.greenfinger;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.github.paganini2008.springdesert.jdbc.annotations.DaoScan;

import indi.atlantis.framework.tridenter.EnableApplicationCluster;
import indi.atlantis.framework.vortex.EnableNioTransport;

/**
 * 
 * EnableGreenFingerServer
 * 
 * @author Fred Feng
 *
 * @version 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@DaoScan(basePackages = "indi.atlantis.framework.greenfinger.jdbc")
@EnableNioTransport
@EnableApplicationCluster(enableLeaderElection = true, enableMonitor = true)
@Import(GreenFingerAutoConfiguration.class)
public @interface EnableGreenFingerServer {
}
