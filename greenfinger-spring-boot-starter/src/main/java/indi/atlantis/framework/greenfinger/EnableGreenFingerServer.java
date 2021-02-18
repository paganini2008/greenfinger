package indi.atlantis.framework.greenfinger;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import indi.atlantis.framework.jdbc.annotations.DaoScan;
import indi.atlantis.framework.seafloor.EnableApplicationCluster;
import indi.atlantis.framework.vortex.EnableNioTransport;

/**
 * 
 * EnableGreenFingerServer
 * 
 * @author Jimmy Hoff
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
