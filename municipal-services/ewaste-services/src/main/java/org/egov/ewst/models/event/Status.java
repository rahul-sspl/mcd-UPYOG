/*
 * eGov suite of products aim to improve the internal efficiency,transparency,
 * accountability and the service delivery of the government  organizations.
 *
 *  Copyright (C) 2016  eGovernments Foundation
 *
 *  The updated version of eGov suite of products as by eGovernments Foundation
 *  is available at http://www.egovernments.org
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see http://www.gnu.org/licenses/ or
 *  http://www.gnu.org/licenses/gpl.html .
 *
 *  In addition to the terms of the GPL license to be adhered to in using this
 *  program, the following additional terms are to be complied with:
 *
 *      1) All versions of this program, verbatim or modified must carry this
 *         Legal Notice.
 *
 *      2) Any misrepresentation of the origin of the material is prohibited. It
 *         is required that all modified versions of this material be marked in
 *         reasonable ways as different from the original version.
 *
 *      3) This license does not grant any rights to any user of the program
 *         with regards to rights under trademark law for use of the trade names
 *         or trademarks of eGovernments Foundation.
 *
 *  In case of any queries, you can reach eGovernments Foundation at contact@egovernments.org.
 */

package org.egov.ewst.models.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents the status of an event in the Ewaste application.
 * This enum contains the possible statuses such as ACTIVE, INACTIVE, CANCELLED, INWORKFLOW, REJECTED, and REQUESTCOMPLETED.
 */
public enum Status {
	ACTIVE("ACTIVE"),
	INACTIVE("INACTIVE"),
	CANCELLED("CANCELLED"),
	INWORKFLOW("INWORKFLOW"),
	REJECTED("REJECTED"),
	REQUESTCOMPLETED("REQUESTCOMPLETED");

	// Value associated with the status
	private String value;

	// Constructor to set the value of the status
	Status(String value) {
		this.value = value;
	}

	/**
	 * Returns the string representation of the status.
	 *
	 * @return the name of the status
	 */
	@Override
	@JsonValue
	public String toString() {
		return name();
	}

	/**
	 * Creates a Status enum from a given string value.
	 *
	 * @param passedValue the string value to convert
	 * @return the corresponding Status enum, or null if no match is found
	 */
	@JsonCreator
	public static Status fromValue(String passedValue) {
		for (Status obj : Status.values()) {
			if (String.valueOf(obj.value).equals(passedValue.toUpperCase())) {
				return obj;
			}
		}
		return null;
	}
}