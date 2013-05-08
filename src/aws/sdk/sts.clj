(ns aws.sdk.sts
  "Functions to access the Amazon Security Token Service.

  Each function takes a map of credentials as its first argument. The
  credentials map should contain an :access-key key and a :secret-key
  key."

  (:import com.amazonaws.AmazonServiceException
           com.amazonaws.auth.BasicAWSCredentials
           com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
           com.amazonaws.services.securitytoken.model.Credentials
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
