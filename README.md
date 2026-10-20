<p align="center"><img src="docs/logo.svg" width="96" alt="" /></p>

# shardplan

shardplan is a command-line planner that splits a test suite into balanced
shards. It is for people who run their tests as a CI matrix, for example 4
parallel jobs that each run one shard.

[![CI](https://github.com/Gulliby-development/shardplan/actions/workflows/ci.yml/badge.svg)](https://github.com/Gulliby-development/shardplan/actions/workflows/ci.yml) [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

The input is a text file with the recorded duration of every test file.

- `shardplan plan` prints the shards with their files and times.
- `shardplan list` prints only the files of one shard, so that a CI job can
  pass them to its test runner.
- `shardplan need` says how many shards are needed so that no shard runs longer
  than a target time.

It works offline, makes no network call, starts no other program and never
changes its input files. The result depends only on the arguments and the input
bytes, so it is the same on Windows, macOS and Linux.

## Install

You need a JDK 21 or later and Maven. Build the classes:

```
mvn -B -q package
```

Then run the program from the project folder:

```
java -cp target/classes io.github.gulliby.shardplan.Main plan --shards 3 timings.txt
```

`mvn package` also builds a runnable jar, so
`java -jar target/shardplan-0.1.0.jar plan --shards 3 timings.txt` works too, and
`shardplan --version` prints `shardplan 0.1.0` from the jar (`shardplan dev` when run from
`target/classes`). The examples below write `shardplan` for either command.

## Usage

`shardplan --help` prints:

```
usage: shardplan plan --shards N [--files LIST] TIMINGS
       shardplan list --shards N --index K [--null] [--files LIST] TIMINGS
       shardplan need --target SECONDS [--max-shards N] [--files LIST] TIMINGS
```

TIMINGS is the path of the timings file, or `-` for standard input. LIST is
always a path. An option is written as `--opt VALUE` or `--opt=VALUE`; options
and TIMINGS may come in any order, and when an option is repeated the last one
wins. `--shards` and `--index` are numbers from 1 to 64 (`--index` at most the
shard count); `--target` is a time in seconds greater than 0.

The examples use this file `timings.txt`:

```
# seconds path
42.5 tests/test_db.py
3.2 tests/test_cli.py
18 tests/test_api.py
27.250 tests/test_parser.py
0.8 tests/test_utils.py
9.1 tests/test_auth.py
```

### plan

`shardplan plan --shards 3 timings.txt` prints every shard with its total time
and its files, then the longest and the shortest shard:

```
shard 1: 42.500 s, 1 file
  42.500 tests/test_db.py
shard 2: 28.050 s, 2 files
  27.250 tests/test_parser.py
  0.800 tests/test_utils.py
shard 3: 30.300 s, 3 files
  18.000 tests/test_api.py
  9.100 tests/test_auth.py
  3.200 tests/test_cli.py
balance: longest 42.500 s (shard 1), shortest 28.050 s (shard 2)
```

When there are more shards than files, the extra shards stay empty and are
printed as `shard 7: 0.000 s, 0 files`.

### list

`shardplan list --shards=3 --index 2 timings.txt` prints the paths of shard 2,
one per line, and nothing when the shard is empty:

```
tests/test_parser.py
tests/test_utils.py
```

With `--null` every path ends with a NUL byte instead of LF, so that a path with
spaces stays in one piece for `xargs -0`. Only `list` takes `--null`, and it
takes no value.

### need

`shardplan need --target 45 timings.txt` prints the smallest number of shards
for which no shard runs longer than 45 seconds:

```
shards needed: 3 (longest 42.500 s)
```

(One shard would take 100.850 s and two shards 51.600 s.) The shard count that
`need` tries goes from 1 to the smaller of 64 and the number of files.

With `--max-shards N` (a number from 1 to 64) `need` tries at most N shards, for
a CI matrix that has only N jobs. `shardplan need --target 45 --max-shards 3
timings.txt` still prints `shards needed: 3 (longest 42.500 s)`, but with
`--max-shards 2` nothing is printed on standard output, the exit code is 1 and
standard error holds:

```
shardplan: the target 45.000 s needs more than 2 shards
```

Without the option the limit is 64, and the message for it is unchanged. Only
`need` takes `--max-shards`.

When no plan reaches the target, nothing is printed on standard output, one line
goes to standard error and the exit code is 1. `shardplan need --target 30
timings.txt` gives:

```
shardplan: tests/test_db.py takes 42.500 s, longer than the target 30.000 s
```

When every file fits the target but 64 shards are not enough, the line is
`shardplan: the target TIME s needs more than 64 shards`.

### --files

The timings file often lags behind the suite: new test files have no timing yet
and deleted ones still have one. `--files LIST` names the test files that exist
today, one path per line. With the list file `files.txt`:

```
# every test file today
tests/test_db.py
tests/test_api.py
tests/test_new.py
tests/test_other.py
```

`shardplan plan --files files.txt --shards 2 timings.txt` prints:

```
shard 1: 60.500 s, 2 files
  18.000 tests/test_api.py
  42.500 tests/test_db.py
shard 2: 60.500 s, 2 files
  30.250 tests/test_new.py (estimated)
  30.250 tests/test_other.py (estimated)
balance: longest 60.500 s (shard 1), shortest 60.500 s (shard 1)
estimated: 2, ignored: 4
```

The two new files get the mean of 42.5 and 18, which is 30.250. The last line
counts the estimated files and the timings that are not in the list. `list` and
`need` take `--files` in the same way: `shardplan need --target 60.5
--files=files.txt timings.txt` prints `shards needed: 2 (longest 60.500 s)`.

### In a CI matrix

Keep `timings.txt` in the repository and give the matrix 4 jobs with the numbers
1 to 4. Each job K calls `shardplan list --shards 4 --index K timings.txt` and
passes the printed paths to its test runner. Every job computes the same plan
from the same file, so each test file runs in exactly one job. Add
`--files LIST` with a fresh list of the test files when the timings may be out
of date, and use `shardplan need` to choose the number of jobs for a time
budget.

## Timings file format

The timings file is UTF-8 text, with an optional byte order mark, and LF or CRLF
line ends; the last line does not need a line end.

- Every line is `SECONDS PATH`, separated by spaces or tabs. Spaces and tabs at
  both ends of a line are removed.
- SECONDS is `N` or `N.F` with 1 to 5 digits before the dot and 1 to 3 digits
  after it: `12`, `0.5`, `3.250`, `99999.999`. Texts such as `.5`, `5.`,
  `1.2345`, `+1`, `1e3` and `1,5` are not valid. `0` is allowed.
- PATH is the rest of the line and may contain spaces (`2 dir with space/b.py`).
  It is taken as it is: no `\` conversion and no normalisation.
- Blank lines and lines that start with `#` are skipped.
- A file holds at most 2000 entries, and a path may appear only once.

An error names the file and the line, for example `shardplan: e.txt:1: missing
path`. The messages are `invalid seconds "TEXT"`, `missing path`, `more than
2000 files` and `duplicate path "PATH"`. A timings file without any entry is the
error `FILE: no files` unless `--files` is given.

The list file of `--files` has one PATH per line, with the same rules for the
byte order mark, line ends, trimming, blank lines, `#` comments, the 2000-file
limit and duplicates. A list without any path is an error. The files to plan are
then the paths of the list, in list order:

- a path that has a timing takes it;
- a path without a timing is estimated with the mean of the timings of the
  listed paths that have one, in whole milliseconds rounded half up, or 1.000 s
  when none of the listed paths has a timing;
- timings of paths that are not in the list are ignored.

## How shards are planned

All arithmetic is in whole milliseconds and nothing is random, so the same input
gives the same plan everywhere.

1. Sort the files by time, longest first; files with equal times are ordered by
   path in ascending ordinal (code unit) order.
2. Put them one by one into the shard that has the smallest total so far; when
   totals are equal the lowest shard number wins.
3. Inside each shard, list the files in ascending ordinal order of path.

Shards beyond the number of files stay empty.

## Exit codes

| Code | Meaning |
| ---- | ------- |
| 0 | Success. |
| 1 | Only for `need`: no plan reaches the target. |
| 2 | A usage error or an input error. |

Every error is one line `shardplan: MESSAGE` on standard error, and standard
output stays empty. In messages a file is named by its argument with every `\`
replaced by `/`, or `<stdin>` for `-`. A file that cannot be read gives `FILE:
cannot read the file`, and bytes that are not valid UTF-8 give `FILE: input is
not valid UTF-8`.

## Development

Run the checks (compile and all tests) with:

```
mvn -B -q verify
```

The tests use JUnit Jupiter only, write only inside temporary folders and need
no network.

## License

MIT, see the `LICENSE` file.

---

Built with BAKED.
