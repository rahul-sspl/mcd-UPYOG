package org.egov.vendor.web.models.vendorcontract.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class UserDetailResponse {

	@JsonProperty("responseInfo")
    ResponseInfo responseInfo;
	
    @JsonProperty("user")
    List<User> user;
}
