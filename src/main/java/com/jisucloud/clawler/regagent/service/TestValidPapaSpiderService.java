package com.jisucloud.clawler.regagent.service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;

import com.deep007.spiderbase.util.BootUtil;
import com.deep007.spiderbase.util.JEmail;
import com.deep007.spiderbase.util.JEmail.JEmailBuilder;
import com.jisucloud.clawler.regagent.interfaces.PapaSpider;
import com.jisucloud.clawler.regagent.interfaces.UsePapaSpider;
import com.jisucloud.clawler.regagent.service.impl.borrow.BangBangTangSpider;
import com.jisucloud.clawler.regagent.service.impl.borrow.GuoShuCaiFuSpider;
import com.jisucloud.clawler.regagent.service.impl.borrow.JuAiCaiSpider;
import com.jisucloud.clawler.regagent.service.impl.borrow.LanCaiWangSpider;
import com.jisucloud.clawler.regagent.service.impl.borrow.PingAnXiaoDaiSpdier;
import com.jisucloud.clawler.regagent.service.impl.borrow.YiDaiWangSpider;
import com.jisucloud.clawler.regagent.service.impl.email.CDMA189EmailSpider;
import com.jisucloud.clawler.regagent.service.impl.email.ENet126EmailSpider;
import com.jisucloud.clawler.regagent.service.impl.email.Enet163EmailSpider;
import com.jisucloud.clawler.regagent.service.impl.email.SohuEmailSpider;
import com.jisucloud.clawler.regagent.service.impl.knowledge.ZhongGuoZhiWangSpider;
import com.jisucloud.clawler.regagent.service.impl.social.QQSpider;
import com.jisucloud.clawler.regagent.util.ReflectUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TestValidPapaSpiderService extends TimerTask implements PapaSpiderTester.PapaSpiderTestListener {
	
	public static final long ONE_DAY_TIME = 3600 * 1000 * 24;
	
	public static Set<Class<? extends PapaSpider>> TEST_SUCCESS_PAPASPIDERS = new HashSet<>();
	public static Set<Class<? extends PapaSpider>> TEST_FAILURE_PAPASPIDERS = new HashSet<>();
	public static Set<Class<? extends PapaSpider>> NOUSE_PAPASPIDERS = new HashSet<>();
	public static Set<Class<? extends PapaSpider>> IGNORE_TEST_RESULT = new HashSet<>();
	
	
	static {
		IGNORE_TEST_RESULT.add(LanCaiWangSpider.class);
		IGNORE_TEST_RESULT.add(JuAiCaiSpider.class);
		IGNORE_TEST_RESULT.add(GuoShuCaiFuSpider.class);
		IGNORE_TEST_RESULT.add(YiDaiWangSpider.class);
		IGNORE_TEST_RESULT.add(PingAnXiaoDaiSpdier.class);
		IGNORE_TEST_RESULT.add(ZhongGuoZhiWangSpider.class);
		IGNORE_TEST_RESULT.add(SohuEmailSpider.class);
		IGNORE_TEST_RESULT.add(CDMA189EmailSpider.class);
		IGNORE_TEST_RESULT.add(ENet126EmailSpider.class);
		IGNORE_TEST_RESULT.add(Enet163EmailSpider.class);
		IGNORE_TEST_RESULT.add(SohuEmailSpider.class);
		IGNORE_TEST_RESULT.add(BangBangTangSpider.class);
		IGNORE_TEST_RESULT.add(QQSpider.class);
	}
	
	private Timer timer = new Timer();
	
	private Set<Class<? extends PapaSpider>> preparedPapaSpiders = new HashSet<>();
	
	@Autowired
	private StringRedisTemplate redisTemplate;
	

	@SuppressWarnings("unchecked")
	@PostConstruct
	private void init() throws Exception {
		try {
			String basePackage = "com.jisucloud.clawler.regagent.service.impl";
			String searchPaths = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
					+ ClassUtils.convertClassNameToResourcePath(basePackage) + "/**/*.class";
			ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
			Resource[] resources = resolver.getResources(searchPaths);
			MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory();
			for (Resource resource : resources) {
				MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
				String className = metadataReader.getClassMetadata().getClassName();
				Class<?> clz = Class.forName(className);
				if (isPapaSpiderClass(clz) && isUsePapaSpider(clz)) {
					preparedPapaSpiders.add((Class<? extends PapaSpider>) clz);
				}else if (isPapaSpiderClass(clz)) {
					NOUSE_PAPASPIDERS.add((Class<? extends PapaSpider>) clz);
				}
			}
			log.info("统计撞库结果，投入使用的" + preparedPapaSpiders.size() + "家，正在研发待投入使用的" + NOUSE_PAPASPIDERS.size() + "家。");
			log.info("正在研发待列表如下：");
			for (Class<?> clz : NOUSE_PAPASPIDERS) {
				log.info(clz.getName());
			}
			timer.schedule(this, 0, 3600 * 1000 * 8);//8小时跑一遍
		}catch(Exception e) {
			log.warn("载入失败", e);
			throw e;
		}
	}
	
	/**
	 * 过去xx小时内测试有效
	 * @param clz
	 * @return
	 */
	private boolean isCheckValidPapaSpiderResultValid(Class<? extends PapaSpider> clz) {
		String value = redisTemplate.opsForValue().get(clz.getName());
		if (value == null) {
			return false;
		}
		return value.equals("true");
	}
	
	/**
	 * 过去xx小时内是否测试过
	 * @param clz
	 * @return
	 */
	private boolean isCheckedPapaSpiderWithOneDay(Class<? extends PapaSpider> clz) {
		return redisTemplate.hasKey(clz.getName());
	}

	public static boolean isPapaSpiderClass(Class<?> clz) {
		return PapaSpider.class.isAssignableFrom(clz);
	}
	
	public static boolean isUsePapaSpider(Class<?> clz) {
		if (ReflectUtil.isUsedAnnotate(UsePapaSpider.class, clz)) {
			UsePapaSpider usePapaSpider = clz.getAnnotation(UsePapaSpider.class);
			return !usePapaSpider.exclude();
		}
		return false;
	}
	
	@Override
	public void testSuccess(Class<? extends PapaSpider> clz) {
		//log.info("测试成功:"+clz.getName());
		addTestSuccessResult(clz);
	}

	public void addTestSuccessResult(Class<? extends PapaSpider> clz) {
		if (TEST_FAILURE_PAPASPIDERS.contains(clz)) {
			TEST_FAILURE_PAPASPIDERS.remove(clz);
		}
		redisTemplate.opsForValue().set(clz.getName(), "true", 3, TimeUnit.DAYS);
		TEST_SUCCESS_PAPASPIDERS.add(clz);
	}

	@Override
	public void testFailure(Class<? extends PapaSpider> clz) {
		if (IGNORE_TEST_RESULT.contains(clz)) {
			//log.warn("忽略测试结果:"+clz.getName());
			addTestSuccessResult(clz);
		}else {
			//log.warn("测试失败:"+clz.getName());
			addTestFailureResult(clz);
		}
		//测试失败之后24小时之内可以不进行测试
		redisTemplate.opsForValue().set(clz.getName(), "false", 1, TimeUnit.DAYS);
	}

	public void addTestFailureResult(Class<? extends PapaSpider> clz) {
		if (TEST_SUCCESS_PAPASPIDERS.contains(clz)) {
			log.info("移除撞库资格:"+clz.getName());
			TEST_SUCCESS_PAPASPIDERS.remove(clz);
		}
		TEST_FAILURE_PAPASPIDERS.add(clz);
	}
	
	@Override
	public void run() {
		JEmailBuilder jemailBuilder = JEmail.builder()
		.fromMail("1273568669@qq.com")
		.username("1273568669@qq.com")
		.password("zbtasvoondmqiici")
		.smtpHost("smtp.qq.com")
		.toMails("guozhong@quicklyun.com");
		jemailBuilder.title(BootUtil.getLocalHostName()+"撞库测试报告 " + new SimpleDateFormat("yyyyMMdd HH:mm:ss").format(new Date()));
		try {
			Set<Class<? extends PapaSpider>> needTestPapaSpiders = new HashSet<>();
			for (Class<? extends PapaSpider> clz : preparedPapaSpiders) {
				if (isCheckValidPapaSpiderResultValid(clz) || IGNORE_TEST_RESULT.contains(clz)) {
					TEST_SUCCESS_PAPASPIDERS.add(clz);
					continue;
				}
				if (!isCheckedPapaSpiderWithOneDay(clz)) {
					needTestPapaSpiders.add(clz);
				}
			}
			if (!System.getProperty("os.name").toLowerCase().contains("mac")) {
				log.info("开始测试......");
				log.info("需要测试"+needTestPapaSpiders.size()+"个,列表如下:");
				int i = 1;
				for (Class<? extends PapaSpider> clz : needTestPapaSpiders) {
					log.info(i + ":" +clz.getName());
				}
				PapaSpiderTester.testing(needTestPapaSpiders, this);
				log.info("测试完成，成功" + TEST_SUCCESS_PAPASPIDERS.size() + "个，失败" + TEST_FAILURE_PAPASPIDERS.size() + "个。");
				jemailBuilder.addContentLine("测试完成，成功" + TEST_SUCCESS_PAPASPIDERS.size() + "个，失败" + TEST_FAILURE_PAPASPIDERS.size() + "个。");
				if (!TEST_FAILURE_PAPASPIDERS.isEmpty()) {
					log.info("测试失败列表如下:");
					jemailBuilder.addContentLine("测试失败列表如下:");
					for (Class<? extends PapaSpider> clz : TEST_FAILURE_PAPASPIDERS) {
						log.info(clz.getName());
						jemailBuilder.addContentLine(clz.getName());
					}
				}
				if (!NOUSE_PAPASPIDERS.isEmpty()) {
					jemailBuilder.addContentLine("");
					log.info("正在研发待列表如下：");
					jemailBuilder.addContentLine("正在研发待列表如下：");
					for (Class<?> clz : NOUSE_PAPASPIDERS) {
						log.info(clz.getName());
						jemailBuilder.addContentLine(clz.getName());
					}
				}
			}
		} catch (Exception e) {
			log.warn("测试中断", e);
			jemailBuilder.addContentLine("测试中断" + e.toString());
		}finally {
			jemailBuilder.build().send();
		}
	}
}
