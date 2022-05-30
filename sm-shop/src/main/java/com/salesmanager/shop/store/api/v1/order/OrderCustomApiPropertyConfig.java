package com.salesmanager.shop.store.api.v1.order;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:shopizer-properties.properties")
@ConfigurationProperties(prefix = "custom")
public class OrderCustomApiPropertyConfig {
	private String url;

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

}
