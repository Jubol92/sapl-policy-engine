package io.sapl.test.steps;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.pdp.AuthorizationSubscription;

/**
* When Step in charge of setting the {@link AuthorizationSubscription} for the test case. 
* Next Step available : {@link ExpectStep}
*/
public interface WhenStep {
   /**
    * Sets the {@link AuthorizationSubscription} for the test case.
    * @param authSubscription the {@link AuthorizationSubscription}
    * @return next available Step {@link ExpectStep}
    */
   ExpectStep when(AuthorizationSubscription  authSubscription);
   /**
    * Sets the {@link AuthorizationSubscription} for the test case.
    * @param authSubscription {@link String} containing JSON defining a {@link AuthorizationSubscription}
    * @return next available Step {@link ExpectStep}
	* @throws JsonProcessingException 
	* @throws JsonMappingException 
    */
   ExpectStep when(String jsonAuthSub) throws JsonProcessingException;	   
   /**
    * Sets the {@link AuthorizationSubscription} for the test case.
    * @param authSubscription {@link ObjectNode} defining a {@link AuthorizationSubscription}
    * @return next available Step {@link ExpectStep}
    */
   ExpectStep when(JsonNode jsonNode);
}