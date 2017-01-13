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
	@NotNull
	private String value;

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

		setValue(args[0]);

		if (args.length > 1) {
			setArgs(Arrays.copyOfRange(args, 1, args.length));
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String[] getArgs() {
		return args;
	}

	public void setArgs(String[] args) {
		this.args = args;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PredicateDefinition that = (PredicateDefinition) o;
		return Objects.equals(name, that.name) &&
				Objects.equals(value, that.value) &&
				Arrays.equals(args, that.args);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, value, args);
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("PredicateDefinition{");
		sb.append("name='").append(name).append('\'');
		sb.append(", value='").append(value).append('\'');
		sb.append(", args=").append(Arrays.toString(args));
		sb.append('}');
		return sb.toString();
	}
}
