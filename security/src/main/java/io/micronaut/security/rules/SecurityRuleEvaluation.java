/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.security.rules;

/**
 * Encapsulates the result of a Security Rule evaluation.
 *
 */
public class SecurityRuleEvaluation {

    private SecurityRuleResult result;

    private SecurityRule rule;

    /**
     * Constructs a SecurityRule evaluation.
     */
    public SecurityRuleEvaluation() {

    }

    /**
     *
     * @param rule The evaluated Rule
     * @param result Result of evaluation
     */
    public SecurityRuleEvaluation(SecurityRule rule, SecurityRuleResult result) {
        this.rule = rule;
        this.result = result;
    }

    /**
     *
     * @return Result of evaluation
     */
    public SecurityRuleResult getResult() {
        return result;
    }

    /**
     *
     * @param result Result of evaluation
     */
    public void setResult(SecurityRuleResult result) {
        this.result = result;
    }

    /**
     *
     * @return The evaluated Rule
     */
    public SecurityRule getRule() {
        return rule;
    }

    /**
     *
     * @param rule The evaluated Rule
     */
    public void setRule(SecurityRule rule) {
        this.rule = rule;
    }
}
