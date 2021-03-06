package com.jisucloud.clawler.regagent.service.impl.game;


import com.jisucloud.clawler.regagent.interfaces.PapaSpider;
import com.jisucloud.clawler.regagent.interfaces.PapaSpiderConfig;
import com.jisucloud.clawler.regagent.util.StringUtil;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;

import java.util.Map;



@Slf4j
@PapaSpiderConfig(
		home = "17173.com", 
		message = "17173是中国游戏第一门户站,全年365天保持不间断更新,您可以在这里获得专业的游戏新闻资讯,完善的游戏攻略专区,人气游戏论坛以及游戏测试账号等,是游戏玩家首选网络。", 
		platform = "17173", 
		platformName = "17173游戏", 
		tags = { "游戏" }, 
		testTelephones = { "18720982607", "18212345678" })
public class _17173Spider extends PapaSpider {

	public boolean checkTelephone(String account) {
		try {
			String url = "https://passport.17173.com/register/validate?field=mobile&value="+account+"&_=" + System.currentTimeMillis();
			Request request = new Request.Builder().url(url)
					.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64; rv:56.0) Gecko/20100101 Firefox/56.0")
					.addHeader("Host", "passport.17173.com")
					.addHeader("Referer", "https://passport.17173.com/register")
					.build();
			Response response = okHttpClient.newCall(request).execute();
			String res = StringUtil.unicodeToString(response.body().string());
			if (res.contains("已经注册")) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean checkEmail(String account) {
		return false;
	}

	@Override
	public Map<String, String> getFields() {
		return null;
	}

}
