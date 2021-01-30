#!/usr/bin/env python3
from pathlib import Path
import shutil

from bs4 import BeautifulSoup as BS # type: ignore

def run() -> None:
    i = Path('~/Downloads/index.html').expanduser()
    shutil.copy(i, Path('index.html.orig'))

    soup = BS(i.read_text(), 'lxml')
    [ldb] = [s for s in soup.find_all('script') if 'logseq_db=' in str(s)]

    graph = "".join(str(item) for item in ldb.contents)
    # todo split by lines?
    Path('logseq_graph.js').write_text(graph)
    ldb.clear()
    ldb['src'] = '/logseq_graph.js'
    i.write_text(str(soup))
    shutil.move(str(i), '.')


def main() -> None:
    from argparse import ArgumentParser as P
    p = P()
    args = p.parse_args()
    run()


if __name__ == '__main__':
    main()
