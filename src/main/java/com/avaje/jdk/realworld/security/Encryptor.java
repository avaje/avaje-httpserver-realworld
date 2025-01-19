package com.avaje.jdk.realworld.security;

import io.avaje.config.Config;
import io.avaje.spi.ServiceProvider;
import io.ebean.DatabaseBuilder;
import io.ebean.config.DatabaseConfigProvider;
import io.ebean.config.EncryptKey;
import io.ebean.config.EncryptKeyManager;

@ServiceProvider
public class Encryptor implements DatabaseConfigProvider {

  @Override
  public void apply(DatabaseBuilder config) {
    config.encryptKeyManager(new BasicEncryptKeyManager());
  }
}

class BasicEncryptKeyManager implements EncryptKeyManager {

  @Override
  public EncryptKey getEncryptKey(String tableName, String columnName) {
    return new CustomEncryptKey(tableName, columnName);
  }

  @Override
  public void initialise() {
    // Do nothing (yet)
  }
}

class CustomEncryptKey implements EncryptKey {

  private final String tableName;

  private final String columnName;

  public CustomEncryptKey(String tableName, String columnName) {
    this.tableName = tableName;
    this.columnName = columnName;
  }

  @Override
  public String getStringValue() {
    return Config.get("application.secret", "secret")
        + "::"
        + this.tableName
        + "::"
        + this.columnName;
  }
}
