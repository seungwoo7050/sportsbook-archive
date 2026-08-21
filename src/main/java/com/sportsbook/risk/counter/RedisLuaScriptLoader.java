package com.sportsbook.risk.counter;

import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Loads immutable classpath Lua scripts with an explicit Redis result shape. */
public final class RedisLuaScriptLoader {
  private RedisLuaScriptLoader() {}

  public static DefaultRedisScript<List> listScript(String name) {
    DefaultRedisScript<List> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("scripts/" + name));
    script.setResultType(List.class);
    return script;
  }
}
