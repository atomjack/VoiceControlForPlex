package com.atomjack.vcfp.interfaces;

import com.atomjack.vcfp.model.Connection;

public interface ActiveConnectionHandler {
  void onSuccess(Connection connection);
  void onFailure(int statusCode);
}
