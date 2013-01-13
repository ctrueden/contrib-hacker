This project hacks your GitHub Contributions Calendar to match an image.

Specifically, given an image file and GitHub username as input, it
produces a shell script that creates and populates a Git repository,
which when pushed to that user's public space will result in that user's
Contributions Calendar matching the specified image.


Why is this in Java?
--------------------

I briefly explored doing it in Python, but PIL is not installed by
default (at least on OS X 10.6).

I then considered doing it in Perl, but an imaging library is not
available by default for Perl either. Nor for Ruby.

Conversely, Java has support for common image formats (e.g., PNG, JPEG,
TIFF and GIF) in the standard library.
