package org.springframework.cloud.gateway.config;

import java.util.Arrays;
import java.util.Objects;

import javax.validation.ValidationException;
import javax.validation.constraints.NotNull;

import static org.springframework.util.StringUtils.tokenizeToStringArray;

/**
 * @author Spencer Gibb
 */
public class PredicateDefinition {
	@NotNull
	private String name;
	private String[] args;

	public PredicateDefinition() {
	}

	public PredicateDefinition(String text) {
		int eqIdx = text.indexOf("=");
		if (eqIdx <= 0) {
			throw new ValidationException("Unable to parse PredicateDefinition text '" + text + "'" +
					", must be of the form name=value");
		}
		setName(text.substring(0, eqIdx));

		String[] args = tokenizeToStringArray(text.substring(eqIdx+1), ",");
		setArgs(args);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String[] getArgs() {
		return args;
	}

	public void setArgs(String... args) {
		this.args = args;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PredicateDefinition that = (PredicateDefinition) o;
		return Objects.equals(name, that.name) &&
				Arrays.equals(args, that.args);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, args);
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("PredicateDefinition{");
		sb.append("name='").append(name).append('\'');
		sb.append(", args=").append(Arrays.toString(args));
		sb.append('}');
		return sb.toString();
	}
}
