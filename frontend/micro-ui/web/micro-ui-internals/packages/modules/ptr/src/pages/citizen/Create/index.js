/**
 * PTRCreate Component
 * 
 * This component handles the pet registration process, including form navigation, data fetching, 
 * and rendering different steps of the application process.
 * 
 * Features:
 * - Uses React hooks for state management, routing, and API calls.
 * - Fetches common fields configuration and application data from the MDMS service.
 * - Supports navigation between different steps of the form.
 * - Caches form data using session storage to persist user inputs.
 * - Renders loading state until data is fetched.
 * - Dynamically renders routes based on configuration.
 * - Handles multi-step form submissions and navigates to acknowledgment or summary pages.
 */

import { Loader } from "@nudmcdgnpm/digit-ui-react-components";
import React, { Fragment, useContext, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "react-query";
import { Redirect, Route, Switch, useHistory, useLocation, useRouteMatch } from "react-router-dom";
import { citizenConfig } from "../../../config/Create/citizenconfig";


const PTRCreate = ({ parentRoute }) => {

  const queryClient = useQueryClient();
  const match = useRouteMatch();
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const history = useHistory();
  const stateId = Digit.ULBService.getStateId();

  let config = [];
  const [params, setParams, clearParams] = Digit.Hooks.useSessionStorage("PTR_CREATE_PET", {});

  // Fetches common field configurations from MDMS
  let { data: commonFields, isLoading } = Digit.Hooks.useEnabledMDMS(Digit.ULBService.getStateId(), "PetService", [{ name: "CommonFieldsConfig" }],
    {
      select: (data) => {
        const formattedData = data?.["PetService"]?.["CommonFieldsConfigEmp"]
        return formattedData;
      },
    });

    // Fetching application ID from session storage
  const applicationId = sessionStorage.getItem("petId") ?sessionStorage.getItem("petId") : null
  sessionStorage.setItem("applicationType",pathname.includes("new-application") ? "NEWAPPLICATION":"RENEWAPPLICATION")
  const tenantId = Digit.ULBService.getCitizenCurrentTenant(true);

  // Fetching application data by ID
  const { isError, error, data: ApplicationData } = Digit.Hooks.ptr.usePTRSearch(
    {
      tenantId,
      filters: { applicationNumber: applicationId },
    },
  );

  let dataComingfromAPI = ApplicationData?.PetRegistrationApplications[0];

/**
   * Navigates to the next form step
   * @param {boolean} skipStep - Whether to skip the next step
   * @param {number} index - Index of the current step
   * @param {boolean} isAddMultiple - Flag for multi-step addition
   * @param {string} key - Current form field key
   */

  const goNext = (skipStep, index, isAddMultiple, key) => {
    let currentPath = pathname.split("/").pop(),
      lastchar = currentPath.charAt(currentPath.length - 1),
      isMultiple = false,
      nextPage;

      // Handles multi-step form navigation
    if (Number(parseInt(currentPath)) || currentPath == "0" || currentPath == "-1") {
      if (currentPath == "-1" || currentPath == "-2") {
        currentPath = pathname.slice(0, -3);
        currentPath = currentPath.split("/").pop();
        isMultiple = true;
      } else {
        currentPath = pathname.slice(0, -2);
        currentPath = currentPath.split("/").pop();
        isMultiple = true;
      }
    } else {
      isMultiple = false;
    }
    if (!isNaN(lastchar)) {
      isMultiple = true;
    }
    // let { nextStep = {} } = config.find((routeObj) => routeObj.route === currentPath);
    let { nextStep = {} } = config.find((routeObj) => routeObj.route === (currentPath || '0'));



    let redirectWithHistory = history.push;
    if (skipStep) {
      redirectWithHistory = history.replace;
    }
    if (isAddMultiple) {
      nextStep = key;
    }
    if (nextStep === null) {
      return redirectWithHistory(`${match.path}/check`);
    }
    if (!isNaN(nextStep.split("/").pop())) {
      nextPage = `${match.path}/${nextStep}`;
    }
    else {
      nextPage = isMultiple && nextStep !== "map" ? `${match.path}/${nextStep}/${index}` : `${match.path}/${nextStep}`;
    }

    redirectWithHistory(nextPage);
  };

 
 // Clears parameters on back navigation
  if(params && Object.keys(params).length>0 && window.location.href.includes("/info") && sessionStorage.getItem("docReqScreenByBack") !== "true")
    {
      clearParams();
      queryClient.invalidateQueries("PTR_CREATE_PET");

    }
 // Navigates to the acknowledgment page
  const ptrcreate = async () => {
    history.push(`${match.path}/acknowledgement`);
  };

 /**
   * Handles form field selection and updates session storage
   * @param {string} key - Field key
   * @param {object} data - Field data
   * @param {boolean} skipStep - Whether to skip the next step
   * @param {number} index - Current step index
   * @param {boolean} isAddMultiple - Whether multiple steps are added
   */

  function handleSelect(key, data, skipStep, index, isAddMultiple = false) {
    if (key === "owners") {
      let owners = params.owners || [];
      owners[index] = data;
      setParams({ ...params, ...{ [key]: [...owners] } });
    } else if (key === "units") {
      let units = params.units || [];
      // if(index){units[index] = data;}else{
      units = data;

      setParams({ ...params, units });
    } else {
      setParams({ ...params, ...{ [key]: { ...params[key], ...data } } });
    }
    goNext(skipStep, index, isAddMultiple, key);
  }

  const handleSkip = () => { };
  const handleMultiple = () => { };

  // Clears params and cache on success
  const onSuccess = () => {
    clearParams();
    queryClient.invalidateQueries("PTR_CREATE_PET");
    sessionStorage.removeItem(["applicationType","petId"]);
    sessionStorage.removeItem("petToken");
  };
  if (isLoading) {
    return <Loader />;
  }

// Merges common fields configuration with citizen configuration
  commonFields = commonFields ? commonFields : citizenConfig;
  commonFields.forEach((obj) => {
    config = config.concat(obj.body.filter((a) => !a.hideInCitizen));
  });

  config.indexRoute = "info";

  const CheckPage = Digit?.ComponentRegistryService?.getComponent("PTRCheckPage");
  const PTRAcknowledgement = Digit?.ComponentRegistryService?.getComponent("PTRAcknowledgement");




  return (
    <Switch>
      {config.map((routeObj, index) => {
        const { component, texts, inputs, key } = routeObj;
        const Component = typeof component === "string" ? Digit.ComponentRegistryService.getComponent(component) : component;
        return (
          <Route path={`${match.path}/${routeObj.route}`} key={index}>
            <Component config={{ texts, inputs, key }} onSelect={handleSelect} onSkip={handleSkip} t={t} formData={params} onAdd={handleMultiple} renewApplication={pathname.includes("new-application") ? {} : dataComingfromAPI} />
          </Route>
        );
      })}


      <Route path={`${match.path}/check`}>
        <CheckPage onSubmit={ptrcreate} value={params} />
      </Route>
      <Route path={`${match.path}/acknowledgement`}>
        <PTRAcknowledgement data={params} onSuccess={onSuccess} />
      </Route>
      <Route>
        <Redirect to={`${match.path}/${config.indexRoute}`} />
      </Route>
    </Switch>
  );
};

export default PTRCreate;