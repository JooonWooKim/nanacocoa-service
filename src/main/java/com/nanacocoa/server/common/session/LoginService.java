package com.nanacocoa.server.common.session;

public interface LoginService {
	void login(String email);
	void logout();
}
