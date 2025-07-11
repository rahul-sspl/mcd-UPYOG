import { FormStep } from "@mcd89/finance-ui-react-components";
import React from "react";

const SelectName = ({ config, onSelect, t, isDisabled }) => {
  return <FormStep config={config} onSelect={onSelect} t={t} isDisabled={isDisabled}></FormStep>;
};

export default SelectName;
