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

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * @author Sebastien Deleuze
 * @author Anton Brok-Volchansky
 *
 */
public class Person {

	private String firstName;
	private String lastName;

	public Person() { }

	public Person(String firstName, String lastName) {
		this.firstName = firstName;
		this.lastName = lastName;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}
	
	@JsonIgnore
	public String getFullName() {
		return this.firstName + " " + this.lastName;
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Person person = (Person) o;
		String personFullName = person.firstName + person.lastName;
		String thisFullName = this.firstName + this.lastName;

		return !(thisFullName != null ? !thisFullName.equals(personFullName) : personFullName != null);
	}

	@Override
	public int hashCode() {
		String thisFullName = this.firstName + this.lastName;
		return thisFullName != null ? thisFullName.hashCode() : 0;
	}

	@Override
	public String toString() {
		return new StringBuilder()
			.append("Person{firstName='")
			.append(this.firstName)
			.append("\', ")
			.append("lastName='")
			.append(this.lastName)
			.append("\'}")
			.toString();
	}
}