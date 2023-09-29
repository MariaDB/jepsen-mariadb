# jepsen.mysql

Jepsen tests for MySQL and MariaDB on Debian Bookworm. You'll need [a Jepsen environment](https://github.com/jepsen-io/jepsen#setting-up-a-jepsen-environment) to run this test.

## Usage

Quickstart for a single-node test with closed-world predicates:

```
lein run test --db mysql --nodes n1 -w closed-predicate --concurrency 30n --rate 1000 --time-limit 120 -i serializable --nemesis none --key-count 40
```

## License

Copyright © 2023 Jepsen, LLC

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
