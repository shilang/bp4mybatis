package cc.kyp82ndlf.base.log;

import cc.kyp82ndlf.base.mybatis.plugins.PermissionHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * Spring 统一日志处理实现类
 * 
 */

@Aspect
@Component
public class LogInfoAdvice {

	private static Log log = LogFactory.getLog("MethodLogs");

	/**
	 * Pointcut 定义Pointcut，Pointcut名称为aspectjMethod,必须无参，无返回值 只是一个标识，并不进行调用
	 */
	@Pointcut("execution(* cc.kyp82ndlf..*Service.*(..))")
	private void aspectJMethod() {
	};

	@Around("aspectJMethod()")
	public Object doAround(ProceedingJoinPoint pjPoint) throws Throwable {
		long t1 = System.currentTimeMillis();

		// ph 不可以跨service执行
		PermissionHelper.ignorePermissionThisTimeEnd();

		if (log.isInfoEnabled()) {
			log.info(" ->  BEGIN <- " + pjPoint.toLongString());// 方法前的操作
			for(int i = 0 ; i< pjPoint.getArgs().length ; i++)
				log.info( " 	->  ARGS  <- " + pjPoint.toShortString() + " # args : " + pjPoint.getArgs()[i]);// 方法前的操作
		}
		Object retval = pjPoint.proceed();// 执行需要Log的方法
		if (log.isInfoEnabled()) {
			log.info(" ->  END <- " + pjPoint.toLongString() +" . cost ["+(System.currentTimeMillis() - t1)+"] ms . ");// 方法后的操作
		}

		return retval;
	}
}