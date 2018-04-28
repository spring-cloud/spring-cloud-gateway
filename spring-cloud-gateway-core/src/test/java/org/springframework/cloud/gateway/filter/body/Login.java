/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.filter.body;

/**
 * Data container for tests.
 * 
 * @author Anton Brok-Volchansky
 *
 */
public class Login {
	
	private String user;
	private String password;
	
	public Login() { }
	
	public Login(String user, String password) {
		this.user = user;
		this.password = password;
	}

	public String getUser() {
		return user;
	}
	
	public void setUser(String user) {
		this.user = user;
	}
	
	public String getPassword() {
		return password;
	}
	
	public void setPassword(String password) {
		this.password = password;
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Login login = (Login) o;
		String userAndPassword = login.user + login.password;
		String thisUserAndPassword = this.user + this.password;

		return !(thisUserAndPassword != null ? !thisUserAndPassword.equals(userAndPassword) : userAndPassword != null);
	}

	@Override
	public int hashCode() {
		String thisUserAndPassword = this.user + this.password;
		return thisUserAndPassword != null ? thisUserAndPassword.hashCode() : 0;
	}

	@Override
	public String toString() {
		return new StringBuilder()
			.append("Login{user='")
			.append(this.user)
			.append("\', ")
			.append("password='")
			.append(this.password)
			.append("\'}")
			.toString();
	}

}
