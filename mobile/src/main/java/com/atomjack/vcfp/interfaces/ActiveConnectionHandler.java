package com.atomjack.vcfp.interfaces;

import com.atomjack.vcfp.model.Connection;

public interface ActiveConnectionHandler {
  public void onSuccess(Connection connection);
  public void onFailure(int statusCode);
}
