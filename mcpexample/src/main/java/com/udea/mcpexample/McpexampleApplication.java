package com.udea.mcpexample;

import com.udea.mcpexample.service.TareaToolsService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class McpexampleApplication {

	public static void main(String[] args) {
		SpringApplication.run(McpexampleApplication.class, args);
	}

	@Bean
	public ToolCallbackProvider tareaTools(TareaToolsService tools){
		return MethodToolCallbackProvider.builder()
				.toolObjects(tools)
				.build();
	}


}
