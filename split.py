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
    # FIXME might need to patch up and replace js/main.js with js/publishing/main.js ??
    # see
    # https://github.com/logseq/logseq/commit/5ce319415ca0f75ac35952fb9b8872ddf32ff2a5#diff-7be9b89e5079c4efd869a933d7b546415e2e749251167a9cac8c09b748bb88b1R87



def main() -> None:
    from argparse import ArgumentParser as P
    p = P()
    args = p.parse_args()
    run()


if __name__ == '__main__':
    main()
