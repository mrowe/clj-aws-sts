[![Build Status](https://buildhive.cloudbees.com/job/mrowe/job/clj-aws-sts/badge/icon)](https://buildhive.cloudbees.com/job/mrowe/job/clj-aws-sts/)

# clj-aws-sts

A Clojure library for accessing Amazon Secure Token Service, based on
the official AWS Java SDK and borrowing heavily from [clj-aws-ec2][]
and James Reeves's [clj-aws-s3][] library.

[clj-aws-ec2]: https://github.com/mrowe/clj-aws-ec2
[clj-aws-s3]: https://github.com/weavejester/clj-aws-s3

## Install

Add the following dependency to your `project.clj` file:

    [clj-aws-sts "0.1.1"]

## Example

```clojure
(require '[aws.sdk.sts :as sts])

(def cred {:access-key "...", :secret-key "..."})

```

### Exception handling

You can catch exceptions and extract details of the error condition:

```clojure
(try
  (route53/do-something cred "a thing")
  (catch Exception e (sts/decode-exception e)))
```

`sts/decode-exception` provides a map with the following keys:

    :error-code
    :error-type
    :service-name
    :status-code


## Documentation

* [API docs](http://mrowe.github.com/clj-aws-sts/)

## History


### 0.1.1

 * Initial release.


## License

Copyright (C) 2013 Michael Rowe

Distributed under the Eclipse Public License, the same as Clojure.
