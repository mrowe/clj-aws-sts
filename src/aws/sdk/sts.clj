(ns aws.sdk.sts
  "Functions to access the Amazon Security Token Service.

  Each function takes a map of credentials as its first argument. The
  credentials map should contain an :access-key key and a :secret-key
  key."

  (:import com.amazonaws.AmazonServiceException
           com.amazonaws.auth.BasicAWSCredentials
           com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
           com.amazonaws.services.securitytoken.model.AssumedRoleUser
           com.amazonaws.services.securitytoken.model.AssumeRoleRequest
           com.amazonaws.services.securitytoken.model.Credentials
           com.amazonaws.services.securitytoken.model.FederatedUser
           com.amazonaws.services.securitytoken.model.GetFederationTokenRequest
           com.amazonaws.services.securitytoken.model.GetSessionTokenRequest
           )

  (:require [clojure.string :as string]))


(defn- sts-client*
  "Create an AmazonStsClient instance from a map of credentials."
  [cred]
  (let [client (AWSSecurityTokenServiceClient.
                (BasicAWSCredentials.
                 (:access-key cred)
                 (:secret-key cred)))]
    client))

(def ^{:private true}
  sts-client
  (memoize sts-client*))


;;
;; convert object graphs to clojure maps
;;

(defprotocol ^{:no-doc true} Mappable
  "Convert a value into a Clojure map."
  (^{:no-doc true} to-map [x] "Return a map of the value."))

(extend-protocol Mappable nil (to-map [_] nil))


;;
;; convert clojure maps to object graphs

(defn- keyword-to-method
  "Convert a dashed keyword to a CamelCase method name"
  [kw]
  (apply str (map string/capitalize (string/split (name kw) #"-"))))

(defn set-fields
  "Use a map of params to call setters on a Java object"
  [obj params]
  (doseq [[k v] params]
    (let [method-name (str "set" (keyword-to-method k))
          method (first (clojure.lang.Reflector/getMethods (.getClass obj) 1 method-name false))
          arg-type (first (.getParameterTypes method))
          arg (if (= arg-type java.lang.Integer) (Integer. v) v)]
      (clojure.lang.Reflector/invokeInstanceMember method-name obj arg)))
  obj)

(defn map->ObjectGraph
  "Transform the map of params to a graph of AWS SDK objects"
  [params]
  (let [keys (keys params)]
    (zipmap keys (map #(params %) keys))))

(defmacro mapper->
  "Creates a function that invokes set-fields on a new object of type
   with mapped parameters."
  [type]
  `(fn [~'params] (set-fields (new ~type) (map->ObjectGraph ~'params))))

;;
;; exceptions
;;

(extend-protocol Mappable
  AmazonServiceException
  (to-map [e]
    {:error-code   (.getErrorCode e)
     :error-type   (.name (.getErrorType e))
     :service-name (.getServiceName e)
     :status-code  (.getStatusCode e)
     :message      (.getMessage e)}))

(defn decode-exceptions
  "Returns a Clojure map containing the details of an AmazonServiceException"
  [& exceptions]
  (map to-map exceptions))


;;
;; session tokens
;;

(extend-protocol Mappable
  Credentials
  (to-map [credentials]
    {:access-key-id     (.getAccessKeyId credentials)
     :secret-access-key (.getSecretAccessKey credentials)
     :session-token     (.getSessionToken credentials)
     :expiration        (.getExpiration credentials)}))

(defn get-session-token
  "Get a set of temporary credentials for an AWS account or IAM user.
   Optionally, pass a map of params including:

     :duration-seconds - the duration, in seconds, that the credentials should remain valid
     :serial-number    - the identification number of the MFA device for the user
     :token-code       - the value provided by the MFA device

   Returns Credentials, a data structure which contains the following keys:

     :access-key-id     - the AccessKeyId ID that identifies the temporary credentials
     :secret-access-key - the Secret Access Key to sign requests
     :session-token     - the security token that users must pass to the service API to use the temporary credentials
     :expiration        - the date on which these credentials expire

   E.g.:
       (sts/get-session-token cred)
       (sts/get-session-token cred { :duration-seconds 3600 })"
  ([cred]
     (to-map (.getCredentials (.getSessionToken (sts-client cred)))))
  ([cred params]
     (to-map (.getCredentials (.getSessionToken (sts-client cred) ((mapper-> GetSessionTokenRequest) params))))))


;;
;; federation tokens
;;

(extend-protocol Mappable
  FederatedUser
  (to-map [federated-user]
    {:arn             (.getArn federated-user)
     :federated-user-id (.getFederatedUserId federated-user)}))


(defn get-federation-token
 "Returns a set of temporary credentials for a federated user with
  the user name and policy specified in the request. It expects the
  following parameter:

    :name - the name of the federated user associated with the credentials

  and optionally:

    :duration-seconds - the duration, in seconds, that the credentials should remain valid
    :policy           - a policy specifying the permissions to associate with the credentials

 See
 http://docs.aws.amazon.com/STS/latest/UsingSTS/FederationPermissions.html
 for more details about specifying permissions in a policy.

 Returns a data structure containing credentials and information
 about the assumed user role:

   {:credentials
     {
      :access-key-id     - the AccessKeyId ID that identifies the temporary credentials
      :secret-access-key - the Secret Access Key to sign requests
      :session-token     - the security token that users must pass to the service API to use the temporary credentials
      :expiration        - the date on which these credentials expire
     }
    :federated-user
     {
      :arn               - the ARN specifying the federated user associated with the credentials
      :federated-user-id - the string identifying the federated user associated with the credentials
     }
   }

 E.g.:
     (sts/get-federation-token cred {:name \"auser\"}"
  [cred params]
 (let [result (.getFederationToken (sts-client cred) ((mapper-> GetFederationTokenRequest) params))]
   {:credentials (to-map (.getCredentials result))
    :federated-user (to-map (.getFederatedUser result))}))

;;
;; assume role
;;

(extend-protocol Mappable
  AssumedRoleUser
  (to-map [assumed-role-user]
    {:arn             (.getArn assumed-role-user)
     :assumed-role-id (.getAssumedRoleId assumed-role-user)}))

(defn assume-role
 "Returns a set of temporary security credentials that you can use to
  access resources that are defined in the role's policy. It expects
  the following parameters:

    :role-arn          - the Amazon Resource Name (ARN) of the role that the caller is assuming
    :role-session-name - an identifier for the assumed role session

  See
  http://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRole.html
  for descriptions of all available parameters.

  Returns a data structure containing credentials and information
  about the assumed user role:

    {:credentials
      {
       :access-key-id     - the AccessKeyId ID that identifies the temporary credentials
       :secret-access-key - the Secret Access Key to sign requests
       :session-token     - the security token that users must pass to the service API to use the temporary credentials
       :expiration        - the date on which these credentials expire
      }
     :assumed-role-user
      {
       :arn             - the ARN specifying the federated user associated with the credentials
       :assumed-role-id - a unique identifier that contains the role ID and the role session name of the role that is being assumed
      }
    }

   E.g.:
       (sts/assume-role cred {:role-arn \"arn:aws:iam::123456789012:role/demo\" :role-session-name \"Demo\" :duration-seconds 1800 })"
 [cred params]
 (let [result (.assumeRole (sts-client cred) ((mapper-> AssumeRoleRequest) params))]
   {:credentials (to-map (.getCredentials result))
    :assumed-role-user (to-map (.getAssumedRoleUser result))}))
