package com.sunriseletting.web.session;

import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.web.servlet.FlashMap;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Stores session attributes in Redis as JSON rather than with Java serialisation, so sessions stay
 * readable when the stored classes change between releases during a rolling update.
 */
@Configuration(proxyBeanMethods = false)
class SessionConfiguration {

    /** Spring Session picks this up by name as the serialiser for everything it writes to Redis. */
    @Bean
    RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        // The Security modules only read types they trust. Anything else web keeps in the session must be listed here.
        BasicPolymorphicTypeValidator.Builder allowedTypes = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(CopyOnWriteArrayList.class)
                .allowIfSubType(FlashMap.class);
        JsonMapper mapper = JsonMapper.builder()
                .addModules(SecurityJacksonModules.getModules(getClass().getClassLoader(), allowedTypes))
                .build();
        return new GenericJacksonJsonRedisSerializer(mapper);
    }
}
