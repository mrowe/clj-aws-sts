[![Build Status](https://buildhive.cloudbees.com/job/mrowe/job/clj-aws-sts/badge/icon)](https://buildhive.cloudbees.com/job/mrowe/job/clj-aws-sts/)

# clj-aws-sts

A Clojure library for accessing Amazon Security Token Service, based
on the official AWS Java SDK and borrowing heavily from
[clj-aws-ec2][] and James Reeves's [clj-aws-s3][] library.

[clj-aws-ec2]: https://github.com/mrowe/clj-aws-ec2
[clj-aws-s3]: https://github.com/weavejester/clj-aws-s3

## Install

Add the following dependency to your `project.clj` file:

    [clj-aws-sts "0.1.3"]

## Example

```clojure
(require '[aws.sdk.sts :as sts])

(def cred {:access-key "...", :secret-key "..."})

(sts/get-session-token)
(sts/get-session-token cred {:duration-seconds 3600 })
(sts/get-session-token cred {:serial-number "GAHT12345678" token-code "123456"})

(sts/get-federation-token cred {:name "auser"})
(sts/get-federation-token cred {:name "auser" :duration-seconds 1800})

(sts/assume-role cred {:role-arn \"arn:aws:iam::123456789012:role/demo\" :role-session-name \"Demo\" :duration-seconds 1800 })"
```

### Exception handling

You can catch exceptions and extract details of the error condition:

```clojure
(try
  (sts/get-session-token cred)
  (catch Exception e (sts/decode-exceptions e)))
```

`sts/decode-exceptions` provides a map with the following keys:

    :error-code
    :error-type
    :service-name
    :status-code


## Documentation

* [API docs](http://mrowe.github.com/clj-aws-sts/)

## History

### 0.1.3

 * Introduced get-federation-token

### 0.1.2

 * get-session-token now takes parameters
 * Introduced assume-role

### 0.1.1

 * Initial release. Provides get-session-token with default params.


## License

Copyright (C) 2013 Michael Rowe

Distributed under the Eclipse Public License, the same as Clojure.
